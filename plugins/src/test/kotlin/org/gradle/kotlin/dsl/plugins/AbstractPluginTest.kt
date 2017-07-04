package org.gradle.kotlin.dsl.plugins

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest
import org.gradle.kotlin.dsl.fixtures.gradleRunnerFor


open class AbstractPluginTest : AbstractIntegrationTest() {

    protected
    fun buildWithPlugin(vararg arguments: String) =
        gradleRunnerFor(projectRoot)
            .withArguments(*arguments)
            .build()!!
}
