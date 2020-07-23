package com.navio.apollo.tests

import com.navio.apollo.casper.ApiFilterRegistrationConfiguration
import com.navio.apollo.Application
import com.navio.apollo.Configuration
import com.navio.apollo.MoreConfig
import com.navio.apollo.casper.CasperTestBase
import com.navio.apollo.casper.DatabaseConfigurationState
import com.navio.apollo.casper.EntityManagerTransactionContext
import com.navio.apollo.casper.TemplateId
import com.navio.apollo.casper.graphql.CasperGraphQLTestBase
import com.navio.apollo.model.MyUser
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.test.context.ContextConfiguration
import javax.persistence.EntityManager
import javax.sql.DataSource

@ImportAutoConfiguration(Configuration::class, MoreConfig::class, ApiFilterRegistrationConfiguration::class)
@ContextConfiguration(classes = [Application::class])
class ApplicationTestsWithFixturesAndDirectEntityManager : CasperGraphQLTestBase() {

    @Autowired
    lateinit var entityManagerTransactionContext: EntityManagerTransactionContext

    companion object {
        val USER_66_PAPA_PALPATINE = MyUser(66, "Papa Palpatine")
        val USER_78_DARK_VADER = MyUser(78, "Dark Vader")
        val USER_77_LUKE = MyUser(77, "Luuuke")
    }

    @Test
    fun `find fixture and local users`() {
        assertFindByIdGraphQL(USER_1_MAJOR_TOM, graphQLTestTemplate)
        assertFindByIdGraphQL(USER_66_PAPA_PALPATINE, graphQLTestTemplate)
        assertFindByIdGraphQL(USER_78_DARK_VADER, graphQLTestTemplate)
        assertFindByIdGraphQL(USER_77_LUKE, graphQLTestTemplate)
    }

    @Test
    fun `find fixture and local users again to verify local setup repeatability`() {
        assertFindByIdGraphQL(USER_1_MAJOR_TOM, graphQLTestTemplate)
        assertFindByIdGraphQL(USER_66_PAPA_PALPATINE, graphQLTestTemplate)
        assertFindByIdGraphQL(USER_78_DARK_VADER, graphQLTestTemplate)
        assertFindByIdGraphQL(USER_77_LUKE, graphQLTestTemplate)
    }

    override fun getTestTemplate(): DatabaseConfigurationState.TemplateConfiguration {
        return EntityManagerFixtureGenerator(
            listOf(USER_1_MAJOR_TOM),
            entityManagerTransactionContext
        )
    }

    override fun localSetUp(testTemplate: DatabaseConfigurationState.IsolatedTestTemplate) {
        super.localSetUp(testTemplate)
        entityManagerTransactionContext.asIs { persist(USER_66_PAPA_PALPATINE) }
        entityManagerTransactionContext.inTransaction { persist(USER_78_DARK_VADER) }
        entityManagerTransactionContext.inNewTransaction { persist(USER_77_LUKE) }
    }
}

data class EntityManagerFixtureGenerator(val initialUsers: List<MyUser>, val entityManagerTransactionContext: EntityManagerTransactionContext) :
    DatabaseConfigurationState.TemplateConfiguration.FixtureGenerator() {

    override fun getTemplateId(): TemplateId {
        return TemplateId(toString())
    }

    override fun toString(): String {
        // THE COMMENT BELOW IS LIKELY SIMILAR TO WHAT IS EXPECTED WITH FIXTURE FILES, THE IMPL HERE
        // IS INTENDED TO BE MORE READABLE FOR TESTING THE CONCEPT

        // Calculate a hash of the files in order to set up a template database
        // val nameHash = eventFiles.fold(0, { acc: Int, file: String -> acc + file.hashCode() })

        return initialUsers.joinToString(separator = "_", transform = { u -> u.id.toString() })
    }

    override fun initialize(templateName: String, datasource: DataSource) {
        // Initialize a template database consisting of just the fixtures requested
        // The template will only be created if it has not yet been initialized
        // Note the need for a transaction context here during template initialization
        entityManagerTransactionContext.inTransaction {
            initialUsers.forEach(this::persist)
        }
    }
}

