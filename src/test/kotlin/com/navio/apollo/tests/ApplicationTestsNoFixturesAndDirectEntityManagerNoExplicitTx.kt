package com.navio.apollo.tests

import com.navio.apollo.casper.ApiFilterRegistrationConfiguration
import com.navio.apollo.Application
import com.navio.apollo.Configuration
import com.navio.apollo.MoreConfig
import com.navio.apollo.casper.DatabaseConfigurationState
import com.navio.apollo.casper.graphql.CasperGraphQLTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.test.context.ContextConfiguration
import javax.persistence.EntityManager

@ImportAutoConfiguration(Configuration::class, MoreConfig::class, ApiFilterRegistrationConfiguration::class)
@ContextConfiguration(classes = [Application::class])
class ApplicationTestsNoFixturesAndDirectEntityManagerNoExplicitTx : CasperGraphQLTestBase() {

    @Autowired
    lateinit var entityManager: EntityManager

    @Test
    fun `context loads without interactions`() {
        // Verify base context loads and tests pass with no entity interaction
    }

    @Test
    fun `retrieve entity created during setup`() {
        assertFindByIdGraphQL(ApplicationTestsWithFixturesAndEntityManager.USER_66_PAPA_PALPATINE, graphQLTestTemplate)
    }

    override fun getTestTemplate(): DatabaseConfigurationState.TemplateConfiguration {
        return DatabaseConfigurationState.TemplateConfiguration.NoAddedFixtures
    }

    override fun localSetUp(testTemplate: DatabaseConfigurationState.IsolatedTestTemplate) {
        super.localSetUp(testTemplate)
        // Here we use the EntityManager directly without requiring an explicit Transaction because
        // one is already present
        entityManager.persist(ApplicationTestsWithFixturesAndEntityManager.USER_66_PAPA_PALPATINE)
    }
}
