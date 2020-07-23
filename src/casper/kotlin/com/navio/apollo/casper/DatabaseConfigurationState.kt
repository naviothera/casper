package com.navio.apollo.casper

import javax.sql.DataSource

sealed class DatabaseConfigurationState {

    /** The initial Database context, before any templates have been generated */
    object Uninitialized : DatabaseConfigurationState()

    /** A fully configured isolated test database; ready for use with the given db name */
    data class IsolatedTestTemplate(val dbName: String) : DatabaseConfigurationState()

    sealed class TemplateConfiguration : DatabaseConfigurationState() {
        /**
         * Contract interface used for initializing a Database Template with additional fixtures.
         *
         * Database Templates are used to provide initial state to one or more tests during execution and
         * reduce overall test setup time.  Each Template encountered will be constructed once per VM execution.
         * Note that on subsequent runs the Templates will be recreated to ensure that changes to starting state
         * are reflected.
         */
        abstract class FixtureGenerator : TemplateConfiguration() {
            /**
             * Return the unique TemplateId to identify this specific database template instance.
             * Templates will be reused for performance so ids must be unique with respect to the
             * initial fixture state they contain.
             */
            abstract fun getTemplateId(): TemplateId

            /**
             * Callback hook to provide template database specific setup.
             * At this point the DataSource is configured and active and has been created from a template of the base schema
             * that was applied via FlyWay. The database for this template can now be modified in any way; note that after
             * this hook returns the database will be disconnected from use so that it can form the basis of a reusable
             * template for actual test database usage for the duration of the VM.
             *
             * @param templateName the name of the template database that was determined
             * @param datasource a DataSource that is currently connected to the database that will become the template
             *  for test databases that utilize the same fixtures; note that it is generally not necessary to use the
             *  DataSource directly -- calls to Spring managed components such as JPA Repositories or direct EntityManager
             *  use should work to initialize the template database within the scope of this function, however a transaction
             *  will need to be set up.  The EntityManagerTransactionContext can be used to do this simply if desired.
             */
            abstract fun initialize(templateName: String, datasource: DataSource)
        }

        /** No fixtures beyond those in the parent template are desired */
        object NoAddedFixtures : TemplateConfiguration()
    }
}

/**
 * Marker class declaring the specific String value as an ID for a template Database.
 * Each distinct template is required to have a unique id to distinguish it from other templates.
 * The id itself is required to be a maximum of 10 hexadecimal characters in order to fit into the
 * naming rules for tables in conjunction with the base db name and unique test name.
 */
data class TemplateId(val id: String) {
    companion object {
        val templateNameRules = Regex("[0-9a-zA-Z_]{1,30}")
    }

    init {
        require(id.matches(templateNameRules)) { "Must be 10 hexadecimal characters or less" }
    }
}
