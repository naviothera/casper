package com.navio.apollo.casper.graphql

import com.graphql.spring.boot.test.GraphQLTestTemplate
import com.navio.apollo.casper.CasperTestBase
import com.navio.apollo.casper.DatabaseConfigurationState
import com.navio.apollo.casper.TestIsolatedDatabaseRoutingFilter.Companion.TEST_DB_HEADER
import org.springframework.beans.factory.annotation.Autowired

/**
 * Base class for Casper test classes that want to test local GraphQL endpoints.
 */
abstract class CasperGraphQLTestBase : CasperTestBase() {

    @Autowired
    lateinit var graphQLTestTemplate: GraphQLTestTemplate

    /**
     * Sets the header expected by Casper's API Filter.
     */
    override fun localSetUp(testTemplate: DatabaseConfigurationState.IsolatedTestTemplate) {
        // Any additional local setup can now proceed with the entities we have already persisted
        log.debug("Setting GraphQLTemplate header: ${testTemplate.dbName}")
        graphQLTestTemplate.addHeader(TEST_DB_HEADER, testTemplate.dbName)
    }

    override fun localTearDown() {
        graphQLTestTemplate.clearHeaders()
    }
}