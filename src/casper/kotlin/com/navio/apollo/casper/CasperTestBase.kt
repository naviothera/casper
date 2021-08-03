package com.navio.apollo.casper

import com.navio.apollo.casper.DatabaseConfigurationState.IsolatedTestTemplate
import com.navio.apollo.casper.DatabaseConfigurationState.Uninitialized
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureDataJpa
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestEntityManager
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import javax.inject.Inject

/**
 * Base class for tests that utilize persistent database state. Also exposes a GraphQLTestTemplate to allow interaction
 * with Spring's WebEnvironment via GraphQL running in a separate thead while sharing the test database context.
 *
 * This class depends on running a Flyway migration to set up the base schema for the root template.  Flyway
 * will look for the migration scripts in the classpath:db/migration/ by default.  See: https://flywaydb.org/
 *
 * This class does not utilize the @Transactional annotation that sets up TestTransactions and rolls them back,
 * instead it generates uniquely named database instances with the configured DataSource that are used for the
 * duration of the test and then dropped.  This means that all transactional behavior exposed is identical to
 * the code that is used by the application in production. See the following for some explicit reasons for this:
 * https://www.javacodegeeks.com/2011/12/spring-pitfalls-transactional-tests.html
 *
 * Using individual databases per test method ensures that no test can potentially interfere with any other
 * test since data is fully isolated.  It also means that there is no need to attempt to manage any sort of
 * data tear-down after a test completes -- tests can focus entirely on the business without worrying about
 * how it might interact with another test, even within the same class.
 *
 * Because this test manages the database setup and connections internally it is not necessary to use
 * @AutoConfigureTestDatabase.
 *
 * Extending classes need to import any required "application" configurations via annotation.  For example:
 *   @ImportAutoConfiguration(Configuration::class, EventConfiguration::class, GraphQLConfiguration::class)
 *   @ContextConfiguration(classes = [Application::class])
 *
 * This class does not use the @GraphQLTest annotation; instead it exposes the GraphQLTestTemplate for use
 * per: https://github.com/graphql-java-kickstart/graphql-spring-boot/issues/182
 */
@RunWith(SpringRunner::class)
@AutoConfigureDataJpa
@AutoConfigureTestEntityManager
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class CasperTestBase {
    companion object {
        val log = LoggerFactory.getLogger(TestIsolatedRoutingDataSource::class.java)
    }

    @Autowired
    private lateinit var dsManager: TestIsolatedTemplatizingDatabaseManager

    @Inject
    private lateinit var entityManagerTransactionContext: EntityManagerTransactionContext

    /**
     * Test setup method invoked during the before method phase to gather the fixture template
     * the test requires.  This is invoked prior to the 'doSetup()' method in order to provide base fixtures
     * on which any additional manual setup can be performed.
     *
     * @return the fixture context for the test
     */
    abstract fun getTestTemplate(): DatabaseConfigurationState.TemplateConfiguration

    /**
     * Callback to the test class to allow set up of any injected resources prior to setting up the db state on
     * a per test basis. This happens before each test (using @Before).
     *
     * If this does not need to happen before each you might consider @BeforeClass for static setup or @PostConstruct
     * for setup that requires injected resources.
     *
     * Implementations that override this method SHOULD call beforeDbSetup on the super class.
     */
    fun beforeDbSetup() {}

    /**
     * Callback to the test class to indicate that fixture data has been loaded and any additional per-test
     * configuration can now proceed using those resources.  This happens before each test (using @Before).
     *
     * Implementations that override this method SHOULD call localSetup on the super class.
     *
     * @param testTemplate the isolated test template configured for this test
     */
    fun localSetUp(testTemplate: IsolatedTestTemplate) {}

    /**
     * Callback after each test has completed execution (using @After).
     *
     * Implementations that override this method SHOULD call localTearDown on the super class.
     */
    fun localTearDown() {}

    /**
     * Locally exposed capture of the JUnit Description for the executing test.
     */
    @get:Rule
    var testDescription: TestDescription = TestDescription()

    /**
     * Base setup method for the Casper framework to initialize a per-test database based on
     * the test template that the test depends on (via {@link #getTestTemplate}).
     */
    @Before
    fun setUp() {
        beforeDbSetup()
        val testContext = dsManager.initializeTestDb(getTestTemplate())
        // Get the new CasperTestContext that has been initialized and set a custom GraphQL header value
        // that we can intercept on the web server to set the unique DB context there
        when (testContext) {
            is IsolatedTestTemplate -> {
                log.info("Test ${testDescription.fullClassAndMethodName()} executing with isolated db: ${testContext.dbName}")
                // The Isolated test Database is ready, proceed with any local setup
                entityManagerTransactionContext.inTransaction { localSetUp(testContext) }
            }
            else -> throw IllegalStateException("Expected to have test database initialized into context?")
        }
    }

    /**
     * Discard the temporary database that was used only for this test execution.
     */
    @After
    fun after() {
        /** Allow tests first to do any local tear down needed before the database is destroyed */
        localTearDown()
        /** Stop any resources connected to the test database and drop it */
        val context = CasperTestContext.getState()
        val threadName = Thread.currentThread().name
        log.debug("Shutting down context: $context for $threadName") // Clean up our current temporary database (drop it) and reset the GraphQL headers and CasperTestContext
        dsManager.cleanUp(context)
        CasperTestContext.setState(Uninitialized)
    }
}
