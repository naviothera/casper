package com.navio.apollo.casper

import com.navio.apollo.casper.DatabaseConfigurationState.IsolatedTestTemplate
import com.navio.apollo.casper.DatabaseConfigurationState.Uninitialized
import com.navio.apollo.memoOf
import org.apache.commons.lang3.RandomStringUtils
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.PropertySource
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import java.io.Closeable
import java.sql.Connection
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

/**
 * Mapping Component containing all of the configured DataSources as well as the means of creating
 * template databases and unique per test databases.
 *
 * This Component provides the "TargetDataSources" for the AbstractRoutingDataSource (CasperRoutingDataSource)
 * when requested.  It also includes a method to "recompute" the current set of sources based on the CasperTestContext
 * and a more specific method used by the CasperTestBase that initializes a new unique database after computing any
 * needed templates.
 */
@Component
@Scope(value = "singleton")
@PropertySource("/application.properties")
class TestIsolatedTemplatizingDatabaseManager {
    companion object {
        val log = LoggerFactory.getLogger(TestIsolatedRoutingDataSource::class.java)
        val DEFAULT = "default"

        // jdbc:postgresql://localhost:5432/database_name
        val DS_URL_PATTERN = Regex("""(?<driver>jdbc:[^:]*)://(?<host>[^/]*)/(?<dbname>[0-9a-zA-Z_]+)(?<options>.*)""")

        /**
         * Supplier of unique (within this run) random Strings that can be appended onto a template name to make
         * a unique test database name.  This uses a lock and optimistic String generation mechanism; probability
         * of string collision is low given 16^8 possible permutations.
         */
        val uniqueIdSupplier = object : () -> String {
            val supplied = ConcurrentHashMap<String, String>()
            override fun invoke(): String {
                var uniqueVal = RandomStringUtils.random(8, "0123456789abcdef")
                while (null != supplied.putIfAbsent(uniqueVal, uniqueVal)) {
                    uniqueVal = RandomStringUtils.random(8, "0123456789abcdef")
                }
                return uniqueVal
            }
        }

        /** Mapping of Database names to DataSources */
        private val constructedSources = ConcurrentHashMap<String, DataSource>()

        /** Mapping of template ids (as known by the app) to Database names as constructed. */
        private val activeTemplates = ConcurrentHashMap<TemplateId, String>()
    }

    @Value("\${spring.datasource.driver-class-name}")
    private lateinit var dsDriverClass: String

    @Value("\${spring.datasource.url}")
    private lateinit var dsUrl: String

    @Value("\${spring.datasource.username}")
    private lateinit var dsUsername: String

    @Value("\${spring.datasource.password}")
    private lateinit var dsPassword: String

    @Value("\${spring.datasource.test.template-dbname:#{null}}")
    private var templateDbName: String? = null
        get() {
            return field?.let {
                return if (it.isNotBlank()) it.trim().take(20) else null
            }
        }

    @Value("\${casper.drop-test-dbs:true}")
    private var dropTestDatabases: Boolean = true

    /**
     * A memoizing supplier for the base TemplateID derived from either a configured template name if
     * specified in the configuration or the database name from the supplied db url (suffixed with _t)
     */
    private val rootDatabaseTemplateId = memoOf {
        TemplateId(
            templateDbName ?: "${getBaseDbName()}_t"
        )
    }

    /**
     * Initializes a new test database for use based on the given template.
     * This will construct a root template database (if it does not already exist) as well as the specific
     * template (if it does not already exist) and finally the new test database to be used.
     */
    fun initializeTestDb(templateGenerator: DatabaseConfigurationState.TemplateConfiguration): DatabaseConfigurationState {
        // Set the CasperTestContext for the duration of the initialization
        val threadName = Thread.currentThread().name
        log.debug("Setting CasperTestContext Template ID: ${getTemplateId(templateGenerator)} for $threadName")
        CasperTestContext.setState(templateGenerator)

        // Before any further templates can be created we need to seed a root template with the base schema using flyway
        val baseTemplateName = getNameAndMaybeCreateTemplateDb(createBaseTemplate, null)

        // From the base template construct the specific template (if none exists)
        val templateDbName = getNameAndMaybeCreateTemplateDb(templateGenerator, baseTemplateName)

        // Having populated the template database, proceed with creating the actual temporary database for this test
        val uniqueTemporaryDb = createUniqueTemporaryDb(templateDbName)

        // Update the CasperTestContext to be the newly created database
        log.warn("Setting CasperTestContext DB Name: $uniqueTemporaryDb for $threadName")
        val dbContextForTest = IsolatedTestTemplate(uniqueTemporaryDb)
        CasperTestContext.setState(dbContextForTest)
        return dbContextForTest
    }

    private fun createUniqueTemporaryDb(baseTemplate: String): String {
        // Ensure we have constructed the base DataSource instance
        constructedSources.computeIfAbsent(DEFAULT, makeSource)
        // Set up the new database, drop any existing copy if it exists
        val uniquePostfix =
            uniqueIdSupplier()
        val uniqueTestDbName = baseTemplate.let { "${it}_$uniquePostfix" }
        val create = baseTemplate.let { "create database $uniqueTestDbName template $it" }
        // Drop the database in the off chance we somehow created one with the same name before and didn't.
        withBaseConnection { connection ->
            connection.prepareCall("drop database if exists $uniqueTestDbName").execute()
            connection.prepareCall(create).execute()
        }
        log.debug("Created Unique DB: $uniqueTestDbName with template $baseTemplate")
        return uniqueTestDbName
    }

    private val createBaseTemplate = object : DatabaseConfigurationState.TemplateConfiguration.FixtureGenerator() {
        override fun getTemplateId(): TemplateId {
            return rootDatabaseTemplateId()
        }

        override fun initialize(templateName: String, datasource: DataSource) {
            // Create the Flyway instance and point it to the database
            val flyway = Flyway.configure().dataSource(datasource).load()
            // Run the migration to get base table structure
            flyway.migrate()
        }
    }

    private fun getNameAndMaybeCreateTemplateDb(
        template: DatabaseConfigurationState.TemplateConfiguration,
        baseTemplate: String?
    ): String {
        var templateId = getTemplateId(template)
        var templateName: String? = activeTemplates.get(templateId)
        /*
        Double get on the map is used here with synchronized (rather than a simpler compute if absent) to reduce the number
        of synchronization points wile still allowing fully safe concurrent access to the resource.  Compute if absent does
        not work because the setup happens further on in the createTemplateDb call, which expects the activeTemplates to
        contain the template being setup as a way to de-reference the actual DS being used.
        */
        if (null == templateName) {
            synchronized(activeTemplates) {
                templateName = activeTemplates.get(templateId)
                if (null == templateName) {
                    templateName = when (template) {
                        is DatabaseConfigurationState.TemplateConfiguration.FixtureGenerator -> {
                            createTemplateDb(templateId, baseTemplate, template)
                        }
                        is DatabaseConfigurationState.TemplateConfiguration.NoAddedFixtures -> {
                            baseTemplate
                        }
                    }
                    activeTemplates.put(templateId, templateName!!)
                }
            }
        }
        return templateName!!
    }

    private fun createTemplateDb(
        templateId: TemplateId,
        baseTemplate: String?,
        initializer: DatabaseConfigurationState.TemplateConfiguration.FixtureGenerator
    ): String {
        // Ensure we have constructed the base DataSource instance
        constructedSources.computeIfAbsent(DEFAULT, makeSource)
        // Set up the new database, drop any existing copy if it exists
        val templateDbName = Optional.ofNullable(baseTemplate)
            .map { "${it}_${templateId.id}" }
            .orElse(templateId.id)
        val create = Optional.ofNullable(baseTemplate)
            .map { "create database $templateDbName template $it" }
            .orElse("create database $templateDbName")
        withBaseConnection { connection ->
            connection.prepareCall("drop database if exists $templateDbName").execute()
            connection.prepareCall(create).execute()
        }
        // Add to the set of active templates so we do not have to do this again (this run)
        activeTemplates.put(templateId, templateDbName)

        // While we are in the process of creating the template we need to share the DataSource with
        // the persistence contexts so we add it temporarily to our constructed sources
        val datasource = makeSource(templateDbName)
        constructedSources.put(templateDbName, datasource)

        // While the template is in our mapping proceed to set it up
        initializer.initialize(templateDbName, datasource)

        // After running the context initialization process we need to shut down the connection to the
        // database in order to use it as a template
        removeAndStop(templateDbName)
        log.debug("Initialized Template: $templateDbName")
        return templateDbName
    }

    /**
     * Determines the DataSource to use for the given DatabaseConfigurationState and returns the name of that source.
     * Invoked from the TestIsolatedRoutingDataSource when a "lookupKey" is requested -- this happens on each new
     * web client request, as well as during initial application startup.
     */
    fun ensureDatasource(databaseConfigurationState: DatabaseConfigurationState): String {
        var dbName: String
        when (databaseConfigurationState) {
            is DatabaseConfigurationState.TemplateConfiguration -> {
                // Ensure we have an active source for the template id in our map and return that name
                val templateId = getTemplateId(databaseConfigurationState)
                log.debug("Obtaining template DB setup connection: $templateId")
                dbName = Optional.ofNullable(activeTemplates.get(templateId))
                    .filter { constructedSources.containsKey(it) }
                    .orElseThrow()
            }
            is Uninitialized -> {
                dbName = DEFAULT
            }
            is IsolatedTestTemplate -> {
                dbName = databaseConfigurationState.dbName
            }
        }
        constructedSources.computeIfAbsent(dbName, makeSource)
        log.debug("Computed context database name: $dbName")
        return dbName
    }

    private fun getTemplateId(templateConfig: DatabaseConfigurationState.TemplateConfiguration): TemplateId {
        return when (templateConfig) {
            is DatabaseConfigurationState.TemplateConfiguration.FixtureGenerator -> {
                templateConfig.getTemplateId()
            }
            is DatabaseConfigurationState.TemplateConfiguration.NoAddedFixtures -> {
                rootDatabaseTemplateId()
            }
        }
    }

    fun cleanUp(databaseConfigurationState: DatabaseConfigurationState) {
        when (databaseConfigurationState) {
            is Uninitialized -> {
                throw IllegalStateException("Cannot clean up the default DataSource")
            }
            is DatabaseConfigurationState.TemplateConfiguration -> {
                throw IllegalStateException("Clean up of templates is not currently supported")
            }
            is IsolatedTestTemplate -> {
                // Remove the source form our constructed sources map and close it if it Supports Closable (e.g. HikariCP)
                val dbName = databaseConfigurationState.dbName
                log.debug("Cleaning up $dbName")
                if (constructedSources.containsKey(dbName)) {
                    removeAndStop(dbName)
                } else log.info("A DataSource was never requested for $dbName")
                // Drop the database we created unless overridden to retain
                if (dropTestDatabases) {
                    withBaseConnection { connection -> connection.prepareCall("drop database $dbName").execute() }
                }
            }
        }
    }

    private fun withBaseConnection(inContext: (Connection) -> Unit) {
        withConnection(
            constructedSources.computeIfAbsent(DEFAULT, makeSource),
            inContext
        )
    }

    private fun withConnection(ds: DataSource, inContext: (Connection) -> Unit) {
        ds.connection.use {
            inContext(it)
        }
    }

    private fun removeAndStop(dbName: String) {
        val dataSource = constructedSources.remove(dbName)!!
        when (dataSource) {
            is Closeable -> dataSource.close()
        }
    }

    private val makeSource: (String) -> DataSource = {
        log.debug("Constructing DataSource for Database: $it")
        val dsBuilder = DataSourceBuilder.create()
        dsBuilder.driverClassName(dsDriverClass)
        dsBuilder.url(replaceDbName(it))
        dsBuilder.username(dsUsername)
        dsBuilder.password(dsPassword)
        dsBuilder.build()
    }

    private val replaceDbName: (String) -> String = {
        when (it) {
            DEFAULT -> dsUrl
            else -> {
                val matcher = DS_URL_PATTERN.toPattern().matcher(dsUrl)
                if (matcher.find()) {
                    matcher.group("driver") + "://" +
                        matcher.group("host") + "/" +
                        it + matcher.group("options")
                } else {
                    throw IllegalArgumentException("Provided DataSource URL did not match expected pattern: could not replace DB name")
                }
            }
        }
    }

    private fun getBaseDbName(): String {
        val matcher = DS_URL_PATTERN.toPattern().matcher(dsUrl)
        if (matcher.find()) {
            return matcher.group("dbname").take(18)
        } else {
            throw IllegalArgumentException("Provided DataSource URL did not match expected pattern: could not extract DB name")
        }
    }

    fun getTargetDataSourceMap(): Map<Any, Any> {
        @Suppress("UNCHECKED_CAST")
        return constructedSources as Map<Any, Any>
    }
}
