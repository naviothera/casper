package com.navio.apollo.tests

import com.navio.apollo.casper.ApiFilterRegistrationConfiguration
import com.navio.apollo.Application
import com.navio.apollo.Configuration
import com.navio.apollo.MoreConfig
import com.navio.apollo.casper.DatabaseConfigurationState
import com.navio.apollo.casper.graphql.CasperGraphQLTestBase
import com.navio.apollo.model.MyUser
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.test.context.ContextConfiguration

@ImportAutoConfiguration(Configuration::class, MoreConfig::class, ApiFilterRegistrationConfiguration::class)
@ContextConfiguration(classes = [Application::class])
class ApplicationTestsNoFixtures : CasperGraphQLTestBase() {

    companion object {
        val USER_1_FRED = MyUser(1, "Fred Friendly")
        val USER_1_ALICE = MyUser(1, "Alice Adversary")
    }

    @Test
    fun `context loads without interactions`() {
        // Verify base context loads and tests pass with no entity interaction
    }

    @Test
    fun `create and retrieve Fred`() {
        assertNoSuchUser(USER_1_ALICE, graphQLTestTemplate)
        // Submit mutation to GraphQL endpoint and inspect response
        assertCreateUserGraphQL(USER_1_FRED, graphQLTestTemplate)
        assertFindByIdGraphQL(USER_1_FRED, graphQLTestTemplate)
    }

    @Test
    fun `create and retrieve Alice`() {
        assertNoSuchUser(USER_1_FRED, graphQLTestTemplate)
        // Submit mutation to GraphQL endpoint and inspect response
        assertCreateUserGraphQL(USER_1_ALICE, graphQLTestTemplate)
        assertFindByIdGraphQL(USER_1_ALICE, graphQLTestTemplate)
    }

    override fun getTestTemplate(): DatabaseConfigurationState.TemplateConfiguration {
        return DatabaseConfigurationState.TemplateConfiguration.NoAddedFixtures
    }
}
