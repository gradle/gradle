package org.gradle.script.lang.kotlin.plugins

import org.gradle.script.lang.kotlin.fixtures.AbstractIntegrationTest
import org.gradle.script.lang.kotlin.fixtures.gradleRunnerFor


open class AbstractPluginTest : AbstractIntegrationTest() {

    protected
    fun buildWithPlugin(vararg arguments: String) =
        gradleRunnerFor(projectRoot)
            .withPluginClasspath()
            .withArguments(*arguments)
            .build()!!
}
