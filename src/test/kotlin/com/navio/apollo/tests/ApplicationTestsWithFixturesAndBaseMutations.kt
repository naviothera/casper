package com.navio.apollo.tests

import com.navio.apollo.Application
import com.navio.apollo.Configuration
import com.navio.apollo.MoreConfig
import com.navio.apollo.casper.ApiFilterRegistrationConfiguration
import com.navio.apollo.casper.DatabaseConfigurationState
import com.navio.apollo.casper.graphql.CasperGraphQLTestBase
import com.navio.apollo.model.MyUser
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.test.context.ContextConfiguration

@ImportAutoConfiguration(Configuration::class, MoreConfig::class, ApiFilterRegistrationConfiguration::class)
@ContextConfiguration(classes = [Application::class])
class ApplicationTestsWithFixturesAndBaseMutations : CasperGraphQLTestBase() {
    @Autowired
    lateinit var myUserEntityLoader: MyUserEntityLoader

    companion object {
        val USER_3_SNEAKY_PETE = MyUser(3, "Sneaky Pete")
    }

    @Test
    fun `verify finding users created by fixtures`() {
        // Get user from fixtures and base mutation
        assertFindByIdGraphQL(USER_2_WALTER_WITHERS, graphQLTestTemplate)
        assertFindByIdGraphQL(USER_3_SNEAKY_PETE, graphQLTestTemplate)
    }

    @Test
    fun `verify finding users created by fixtures and base mutations again to verify repeatability`() {
        assertFindByIdGraphQL(USER_2_WALTER_WITHERS, graphQLTestTemplate)
        assertFindByIdGraphQL(USER_3_SNEAKY_PETE, graphQLTestTemplate)
    }

    override fun getTestTemplate(): DatabaseConfigurationState.TemplateConfiguration {
        return MyUserFixtureGenerator(
            listOf(USER_2_WALTER_WITHERS),
            myUserEntityLoader
        )
    }

    override fun localSetUp(testTemplate: DatabaseConfigurationState.IsolatedTestTemplate) {
        super.localSetUp(testTemplate)
        assertCreateUserGraphQL(USER_3_SNEAKY_PETE, graphQLTestTemplate)
    }
}
