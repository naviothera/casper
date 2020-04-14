package com.navio.apollo.casper

import java.sql.Connection
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource
import org.springframework.stereotype.Component

@Component
class TestIsolatedRoutingDataSource() : AbstractRoutingDataSource() {
    companion object {
        val log = LoggerFactory.getLogger(TestIsolatedRoutingDataSource::class.java)
    }

    lateinit var dsManager: TestIsolatedTemplatizingDatabaseManager

    override fun determineCurrentLookupKey(): Any? {
        val testDatabaseState = CasperTestContext.getState()
        val threadName = Thread.currentThread().name
        log.debug("Initial context received: $testDatabaseState for $threadName")

        // Invoke the `recompute` method on the CasperDatasourceManager; if the indicated context does not
        // already have a DataSource constructed it will be set-up at this point and the name (which is used
        // as the key in the DS Map) will be returned here
        val computedDbName = dsManager.ensureDatasource(testDatabaseState)

        // Modify the local set of TargetDataSources that the CasperRoutingDataSource supports to include
        // those that the DataSourceMap contains
        setTargetDataSources(dsManager.getTargetDataSourceMap())

        // Having modified the set it is necessary to invoke the `afterPropertiesSet` Method
        // In order for the locally "resolved" set of DataSources to be updated from the target set
        afterPropertiesSet()

        // Update the CasperTestContext to point at the recomputed key; it may be the same as the incoming
        // value but will be different for the default context as well as during template database initialization
        CasperTestContext.setState(DatabaseConfigurationState.IsolatedTestTemplate(computedDbName))
        log.debug("Database determined: $computedDbName for $threadName")
        return computedDbName
    }

    override fun getConnection(): Connection {
        log.trace("Getting a connection to use....")
        return super.getConnection()
    }

    @Autowired
    fun init(testIsolatedTemplatizingDatasourceManager: TestIsolatedTemplatizingDatabaseManager) {
        // When injecting the CasperDatasourceManager initially we need to set the default TargetDataSources
        // so that we will not encounter an error when the `afterPropertiesSet` method is initially invoked
        // on this Bean
        dsManager = testIsolatedTemplatizingDatasourceManager
        log.debug("initializing dsMap")
        setTargetDataSources(dsManager.getTargetDataSourceMap())
    }
}
