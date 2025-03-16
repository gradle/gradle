/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.enterprise

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import org.gradle.process.ShellScript
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.internal.ToBeImplemented

import javax.inject.Inject

abstract class AbstractDevelocityInputIgnoringServiceIntegrationTest extends AbstractIntegrationSpec {

    def plugin = new GradleEnterprisePluginCheckInFixture(testDirectory, mavenRepo, createExecuter())

    def setup() {
        settingsFile << plugin.pluginManagement() << plugin.plugins()
        plugin.publishDummyPlugin(executer)
    }

    abstract String runIgnoringInputs(String code);

    @Requires(IntegTestPreconditions.IsConfigCached)
    def "configuration inputs are can be ignored"() {
        def configurationCache = new ConfigurationCacheFixture(this)
        given:
        buildFile << """
            ${runIgnoringInputs('println "backgroundJob.property = ${System.getProperty("property")}"')}

            task check {}
        """

        when:
        succeeds("check", "-Dproperty=value")

        then:
        outputContains("backgroundJob.property = value")
        configurationCache.assertStateStored()

        when:
        succeeds("check", "-Dproperty=other")

        then:
        configurationCache.assertStateLoaded()
    }

    @Requires(IntegTestPreconditions.IsConfigCached)
    @ToBeImplemented("https://github.com/gradle/gradle/issues/25474")
    def "value sources are can be ignored"() {
        def configurationCache = new ConfigurationCacheFixture(this)

        given:
        buildFile << """
            def propertyProvider = providers.systemProperty("property")
            ${runIgnoringInputs('println "backgroundJob.property = ${propertyProvider.get()}"')}

            task check {}
        """

        when:
        succeeds("check", "-Dproperty=value")

        then:
        outputContains("backgroundJob.property = value")
        configurationCache.assertStateStored()

        when:
        succeeds("check", "-Dproperty=other")

        then:
        // TODO(mlopatkin) Accessing the value source in the background job should not make it an input,
        //  so the configuration should be loaded from the cache. A naive solution of gating the input
        //  recording will break the other test as only the first value source read is broadcasted to
        //  listeners.
        configurationCache.assertStateStored() // TODO: replace with .assertStateLoaded() once the above is implemented
        outputContains("backgroundJob.property = other")
    }

    @Requires(IntegTestPreconditions.IsConfigCached)
    def "value sources are tracked if also accessed outside the ignored block"() {
        def configurationCache = new ConfigurationCacheFixture(this)

        given:
        buildFile << """
            def propertyProvider = providers.systemProperty("property")
            ${runIgnoringInputs('println "backgroundJob.property = ${propertyProvider.get()}"')}

            println "buildscript.property = \${propertyProvider.get()}"

            task check {}
        """

        when:
        succeeds("check", "-Dproperty=value")

        then:
        outputContains("backgroundJob.property = value")
        outputContains("buildscript.property = value")
        configurationCache.assertStateStored()

        when:
        succeeds("check", "-Dproperty=other")

        then:
        outputContains("backgroundJob.property = other")
        outputContains("buildscript.property = other")
        configurationCache.assertStateStored()
    }

    def "can execute external process with process API at configuration time"() {
        given:
        ShellScript script = ShellScript.builder().printText("Hello, world").writeTo(testDirectory, "script")

        buildFile << """
            ${runIgnoringInputs("""
                def process = ${ShellScript.cmdToStringLiteral(script.getRelativeCommandLine(testDirectory))}.execute()
                process.waitForProcessOutput(System.out, System.err)
            """)}

            task check {}
        """

        when:
        succeeds("check")

        then:
        outputContains("Hello, world")
    }

    def "can execute external process with Gradle API at configuration time"() {
        given:
        ShellScript script = ShellScript.builder().printText("Hello, world").writeTo(testDirectory, "script")

        buildFile << """
            import ${Inject.name}

            interface ExecOperationsGetter {
                @Inject ExecOperations getExecOps()
            }

            def execOperations = objects.newInstance(ExecOperationsGetter).execOps

            ${runIgnoringInputs("""
                execOperations.exec {
                    commandLine(${ShellScript.cmdToVarargLiterals(script.getRelativeCommandLine(testDirectory))})
                }
            """)}

            task check {}
        """

        when:
        succeeds("check")

        then:
        outputContains("Hello, world")
    }
}
