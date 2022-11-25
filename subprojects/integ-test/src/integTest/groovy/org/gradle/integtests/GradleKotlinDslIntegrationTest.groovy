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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.server.http.HttpServer
import spock.lang.Issue

class GradleKotlinDslIntegrationTest extends AbstractIntegrationSpec {

    @Override
    protected String getDefaultBuildFileName() {
        'build.gradle.kts'
    }

    def setup() {
        settingsFile << "rootProject.buildFileName = '$defaultBuildFileName'"
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
        outputContains('it works!')
    }

    @LeaksFileHandles
    def 'can apply Groovy script from url'() {
        given:
        executer.requireOwnGradleUserHomeDir() //we need an empty external resource cache
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
                from("${server.uri}/script.gradle")
            }
        """

        server.expectGet('/script.gradle', scriptFile)

        when:
        run 'hello'

        then:
        outputContains("Hello!")

        when:
        server.stop()
        args("--offline")

        then:
        succeeds 'hello'
        outputContains("Hello!")

        cleanup: // wait for all daemons to shutdown so the test dir can be deleted
        executer.cleanup()
    }

    @LeaksFileHandles
    def 'can apply Kotlin script from url'() {
        given:
        executer.requireOwnGradleUserHomeDir() //we need an empty external resource cache
        HttpServer server = new HttpServer()
        server.start()

        def scriptFile = file("script.gradle.kts") << """
            tasks {
                register("hello") {
                    doLast {
                        println("Hello!")
                    }
                }
            }
        """
        server.expectGet('/script.gradle.kts', scriptFile)

        buildFile << """apply { from("${server.uri}/script.gradle.kts") }"""

        when:
        succeeds 'hello'

        then:
        outputContains("Hello!")

        when:
        server.stop()
        args("--offline")

        then:
        succeeds 'hello'
        outputContains("Hello!")

        cleanup: // wait for all daemons to shutdown so the test dir can be deleted
        executer.cleanup()
    }

    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def 'can query KotlinBuildScriptModel'() {
        given:
        // TODO Remove this once the Kotlin DSL upgrades 'pattern("layout") {' to 'patternLayout {
        // Using expectDeprecationWarning did not work as some setup do not trigger one
        executer.noDeprecationChecks()
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
        succeeds 'dumpKotlinBuildScriptModelClassPath'

        then:
        outputContains("gradle-kotlin-dsl!")
    }

    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def 'can use Kotlin lambda as path notation'() {
        given:
        buildFile << """
            task("listFiles") {
                doLast {

                    // via FileResolver
                    val f = file { "cathedral" }
                    println(f.name)

                    // on FileCollection
                    val collection = layout.files(
                        // single lambda
                        { "foo" },
                        // nested deferred
                        { { "bar" } },
                        // nested unpacking
                        { file({ "baz" }) },
                        // nested both
                        { { file({ { "bazar" } }) } }
                    )
                    println(collection.files.map { it.name })
                }
            }
        """
        when:
        succeeds 'listFiles'
        then:
        outputContains 'cathedral'
        outputContains '[foo, bar, baz, bazar]'
    }

    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def 'can use Kotlin lambda as input property'() {
        given:
        buildFile << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            open class PrintInputToFile @Inject constructor(objects: ObjectFactory): DefaultTask() {
                @get:Input
                val input = { project.property("inputString") }
                @get:OutputFile
                val outputFile: RegularFileProperty = objects.fileProperty()

                @TaskAction fun run() {
                    outputFile.get().asFile.writeText(input() as String)
                }
            }

            task<PrintInputToFile>("writeInputToFile") {
                outputFile.set(project.layout.buildDirectory.file("output.txt"))
            }

        """
        def taskName = ":writeInputToFile"

        when:
        run taskName, '-PinputString=string1'
        then:
        executedAndNotSkipped(taskName)

        when:
        run taskName, '-PinputString=string1'
        then:
        skipped(taskName)

        when:
        run taskName, '-PinputString=string2'
        then:
        executedAndNotSkipped(taskName)
    }

    @Issue("https://youtrack.jetbrains.com/issue/KT-36297")
    def 'can use Kotlin lambda as provider'() {
        given:
        buildFile << '''
            tasks {
                register<Task>("broken") {
                    val prop = objects.property(String::class.java)
                    // broken by https://youtrack.jetbrains.com/issue/KT-36297
                    prop.set(provider { "abc" })
                    doLast { println("-> value = ${prop.get()}") }
                }
                register<Task>("ok1") {
                    val prop = objects.property(String::class.java)
                    val function = { "abc" }
                    prop.set(provider(function))
                    doLast { println("-> value = ${prop.get()}") }
                }
            }
            tasks.register<Task>("ok2") {
                val prop = objects.property(String::class.java)
                prop.set(provider { "abc" })
                doLast { println("-> value = ${prop.get()}") }
            }
        '''

        when:
        succeeds 'broken', 'ok1', 'ok2'

        then:
        result.output.count('-> value = abc') == 3
    }
}
