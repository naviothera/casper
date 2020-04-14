package com.navio.apollo.casper

import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Simple JUnit TestWatcher used to capture the Description
 * of the executing test and method.
 */
class TestDescription : TestWatcher() {
    lateinit var description: Description

    override fun starting(d: Description) {
        description = d
    }

    fun fullClassAndMethodName(): String{
        return "${description.className}.${description.methodName}"
    }
}

