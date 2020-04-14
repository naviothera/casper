package com.navio.apollo.tests

import com.navio.apollo.casper.ApiFilterRegistrationConfiguration
import com.navio.apollo.Application
import com.navio.apollo.Configuration
import com.navio.apollo.MoreConfig
import com.navio.apollo.casper.CasperTestBase
import com.navio.apollo.casper.DatabaseConfigurationState
import com.navio.apollo.casper.graphql.CasperGraphQLTestBase
import com.navio.apollo.model.MyUser
import com.navio.apollo.repositories.MyUserRepo
import graphql.Assert.assertTrue
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.test.context.ContextConfiguration

@ImportAutoConfiguration(Configuration::class, MoreConfig::class, ApiFilterRegistrationConfiguration::class)
@ContextConfiguration(classes = [Application::class])
class ApplicationTestsWithFixturesAndJpaRepo : CasperGraphQLTestBase() {
    @Autowired
    lateinit var myUserEntityLoader: MyUserEntityLoader

    @Autowired
    lateinit var repo: MyUserRepo

    companion object {
        val USER_66_PAPA_PALPATINE = MyUser(66, "Papa Palpatine")
    }

    @Test
    fun `find fixture and local users`() {
        assertFindByIdGraphQL(USER_1_MAJOR_TOM, graphQLTestTemplate)
        assertFindByIdGraphQL(USER_66_PAPA_PALPATINE, graphQLTestTemplate)
    }

    @Test
    fun `find fixture and local users again to verify local setup repeatability`() {
        assertFindByIdGraphQL(USER_1_MAJOR_TOM, graphQLTestTemplate)
        assertFindByIdGraphQL(USER_66_PAPA_PALPATINE, graphQLTestTemplate)
    }

    override fun getTestTemplate(): DatabaseConfigurationState.TemplateConfiguration {
        return MyUserFixtureGenerator(
            listOf(USER_1_MAJOR_TOM),
            myUserEntityLoader
        )
    }

    override fun localSetUp(testTemplate: DatabaseConfigurationState.IsolatedTestTemplate) {
        super.localSetUp(testTemplate)
        // Verify we can find the user created in our fixture in our local repo as well as add another entity
        // to the local repo before executing our tests
        val user = repo.findById(USER_1_MAJOR_TOM.id)
        assertTrue(user.isPresent)
        assertEquals(USER_1_MAJOR_TOM.name, user.map { it.name }.orElseThrow())
        repo.save(USER_66_PAPA_PALPATINE)
    }
}
