package com.navio.apollo.tests

import com.navio.apollo.casper.DatabaseConfigurationState
import com.navio.apollo.casper.TemplateId
import com.navio.apollo.model.MyUser
import java.util.Objects
import javax.sql.DataSource

data class MyUserFixtureGenerator(val initialUsers: List<MyUser>, val myUserEntityLoader: MyUserEntityLoader) :
    DatabaseConfigurationState.TemplateConfiguration.FixtureGenerator() {

    override fun getTemplateId(): TemplateId {
        return TemplateId(toString())
    }

    override fun getFixtureHash(): String {
        return Objects.hashCode(initialUsers).toString()
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
        initialUsers.forEach(myUserEntityLoader::persistUser)
    }
}
