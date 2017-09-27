/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.scan.config.fixtures.BuildScanAutoApplyFixture
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.Requires
import org.gradle.util.ToBeImplemented
import spock.lang.Ignore

import static org.gradle.initialization.StartParameterBuildOptionFactory.BuildScanOption
import static org.gradle.internal.scan.config.BuildScanPluginAutoApply.BUILD_SCAN_PLUGIN_AUTO_APPLY_VERSION
import static org.gradle.internal.scan.config.fixtures.BuildScanAutoApplyFixture.PUBLISHING_BUILD_SCAN_MESSAGE_PREFIX
import static org.gradle.util.TestPrecondition.KOTLIN_SCRIPT
import static org.gradle.util.TestPrecondition.NOT_WINDOWS

@Requires([KOTLIN_SCRIPT, NOT_WINDOWS])
class GradleKotlinDslIntegrationTest extends AbstractIntegrationSpec {

    private final BuildScanAutoApplyFixture fixture = new BuildScanAutoApplyFixture(testDirectory, mavenRepo)

    @Override
    protected String getDefaultBuildFileName() {
        'build.gradle.kts'
    }

    def setup() {
        settingsFile << settingsBuildFileName()
    }

    def 'can run a simple task'() {
        given:
        buildFile << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            
            open class SimpleTask : DefaultTask() {
                @TaskAction fun run() = println("it works!")
            }

            task<SimpleTask>("build")
        """

        when:
        run 'build'

        then:
        result.output.contains('it works!')
    }

    def 'can apply Groovy script from url'() {
        given:
        HttpServer server = new HttpServer()
        server.start()

        def scriptFile = file("script.gradle") << """
            task hello {
                doLast {
                    println "Hello!"
                }
            }
        """

        buildFile << """
            apply { 
                from("http://localhost:${server.port}/script.gradle") 
            }
        """

        server.expectGet('/script.gradle', scriptFile)

        when:
        run 'hello'

        then:
        result.output.contains("Hello!")

        when:
        server.stop()
        args("--offline")

        then:
        succeeds 'hello'
        result.output.contains("Hello!")
    }

    def 'can apply Kotlin script from url'() {
        given:
        HttpServer server = new HttpServer()
        server.start()

        def scriptFile = file("script.gradle.kts") << """
            tasks {
                "hello" {
                    doLast { 
                        println("Hello!") 
                    }
                }
            }
        """
        server.expectGet('/script.gradle.kts', scriptFile)

        buildFile << """apply { from("http://localhost:${server.port}/script.gradle.kts") }"""

        when:
        run 'hello'

        then:
        result.output.contains("Hello!")

        when:
        server.stop()
        args("--offline")

        then:
        succeeds 'hello'
        result.output.contains("Hello!")
    }

    def 'can query KotlinBuildScriptModel'() {
        given:
        // This test breaks encapsulation a bit in the interest of ensuring Gradle Kotlin DSL use
        // of internal APIs is not broken by refactorings on the Gradle side
        buildFile << """
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

task("dumpKotlinBuildScriptModelClassPath") {
    doLast {
        val modelName = KotlinBuildScriptModel::class.qualifiedName
        val builderRegistry = (project as ProjectInternal).services[ToolingModelBuilderRegistry::class.java]
        val builder = builderRegistry.getBuilder(modelName)
        val model = builder.buildAll(modelName, project) as KotlinBuildScriptModel
        if (model.classPath.any { it.name.startsWith("gradle-kotlin-dsl") }) {
            println("gradle-kotlin-dsl!")
        }
    }
}
        """

        when:
        run 'dumpKotlinBuildScriptModelClassPath'

        then:
        result.output.contains("gradle-kotlin-dsl!")
    }

    @ToBeImplemented
    @Ignore
    def "can automatically apply build scan plugin when --scan is provided on command-line"() {
        given:
        buildFile << """
            task("dummy")
        """

        settingsFile.text = """
            ${fixture.pluginManagement()}
            ${settingsBuildFileName()}
        """

        fixture.publishDummyBuildScanPlugin(BUILD_SCAN_PLUGIN_AUTO_APPLY_VERSION, executer)

        when:
        args("--${BuildScanOption.LONG_OPTION}")
        succeeds('dummy')

        then:
        output.contains("${PUBLISHING_BUILD_SCAN_MESSAGE_PREFIX}v${BUILD_SCAN_PLUGIN_AUTO_APPLY_VERSION}")
    }

    private void settingsBuildFileName() {
        """
            "rootProject.buildFileName = '$defaultBuildFileName'"
        """
    }
}
