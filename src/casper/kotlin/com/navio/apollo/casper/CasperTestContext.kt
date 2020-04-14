package com.navio.apollo.casper

/**
 * Object holding a ThreadLocal DatabaseConfigurationState which is used to share the current
 * database contextual state between managed beans operating in isolation but on the same Thread.
 * Notably this is used to provide information to the AbstractRoutingDataSource implementation:
 * CasperRoutingDataSource.  Initially this will be the default context until it is set otherwise.
 */
object CasperTestContext {
    val INSTANCE = ThreadLocal.withInitial<DatabaseConfigurationState> {
        DatabaseConfigurationState.Uninitialized
    }

    fun getState(): DatabaseConfigurationState = INSTANCE.get()
    fun setState(dbContext: DatabaseConfigurationState): Unit = INSTANCE.set(dbContext)
}
