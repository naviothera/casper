package com.navio.apollo.tests

import com.navio.apollo.casper.ApiFilterRegistrationConfiguration
import com.navio.apollo.Application
import com.navio.apollo.Configuration
import com.navio.apollo.MoreConfig
import com.navio.apollo.casper.CasperTestBase
import com.navio.apollo.casper.DatabaseConfigurationState
import com.navio.apollo.casper.graphql.CasperGraphQLTestBase
import com.navio.apollo.model.MyUser
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.test.context.ContextConfiguration

@ImportAutoConfiguration(Configuration::class, MoreConfig::class, ApiFilterRegistrationConfiguration::class)
@ContextConfiguration(classes = [Application::class])
class ApplicationTestsWithFixtures : CasperGraphQLTestBase() {
    @Autowired
    lateinit var myUserEntityLoader: MyUserEntityLoader

    companion object {
        val USER_3_PARANOID_PETE = MyUser(3, "Paranoid Pete")
    }

    @Test
    fun `verify finding users created by fixtures`() {
        // Get users from fixtures
        assertFindByIdGraphQL(USER_4_CHEATER_CHEATERSON, graphQLTestTemplate)
        assertFindByIdGraphQL(USER_5_TOM_PETERSON, graphQLTestTemplate)
        // Make another user as well
        assertCreateUserGraphQL(USER_3_PARANOID_PETE, graphQLTestTemplate)
        assertFindByIdGraphQL(USER_3_PARANOID_PETE, graphQLTestTemplate)
    }

    @Test
    fun `verify finding users created by fixtures again to verify repeatability`() {
        assertFindByIdGraphQL(USER_4_CHEATER_CHEATERSON, graphQLTestTemplate)
        assertFindByIdGraphQL(USER_5_TOM_PETERSON, graphQLTestTemplate)
        assertNoSuchUser(USER_3_PARANOID_PETE, graphQLTestTemplate)
    }

    override fun getTestTemplate(): DatabaseConfigurationState.TemplateConfiguration {
        return MyUserFixtureGenerator(
            listOf(
                USER_4_CHEATER_CHEATERSON,
                USER_5_TOM_PETERSON
            ),
            myUserEntityLoader
        )
    }
}
