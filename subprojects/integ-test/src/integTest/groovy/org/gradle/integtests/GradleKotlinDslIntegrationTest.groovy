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
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.Requires
import spock.lang.Ignore

import static org.gradle.util.TestPrecondition.KOTLIN_SCRIPT

@Requires([KOTLIN_SCRIPT])
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
        result.output.contains('it works!')
    }

    def 'can run a custom task with constructor arguments via API'() {
        given:
        buildFile << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            import javax.inject.Inject

            open class CustomTask @Inject constructor(private val message: String, private val number: Int) : DefaultTask() {
                @TaskAction fun run() = println("\$message \$number")
            }

            tasks.create("myTask", CustomTask::class.java, "hello", 42)
        """

        when:
        run 'myTask'

        then:
        result.output.contains('hello 42')
    }

    @Ignore
    def 'can run custom task with constructor arguments via extension'() {
        given:
        buildFile << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            import javax.inject.Inject

            open class CustomTask @Inject constructor(private val message: String, private val number: Int) : DefaultTask() {
                @TaskAction fun run() = println("\$message \$number")
            }

            task<CustomTask>("myTask", "hello", 42)
        """

        when:
        run 'myTask'

        then:
        result.output.contains('hello 42')
    }

    def "fails to build custom task if constructor arguments missing"() {
        given:
        buildFile << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            import javax.inject.Inject

            open class CustomTask @Inject constructor(private val message: String, private val number: Int) : DefaultTask() {
                @TaskAction fun run() = println("\$message \$number")
            }

            tasks.create("myTask", CustomTask::class.java, "hello")
        """

        when:
        fails 'myTask'

        then:
        result.output.contains("org.gradle.internal.service.UnknownServiceException: No service of type int available")
    }

    def "can construct a task with @Inject services"() {
        given:
        buildFile << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            import org.gradle.workers.WorkerExecutor
            import javax.inject.Inject

            open class CustomTask @Inject constructor(private val executor: WorkerExecutor) : DefaultTask() {
                @TaskAction fun run() = println(if (executor != null) "got it" else "NOT IT")
            }

            task<CustomTask>("myTask")
        """

        when:
        run 'myTask'

        then:
        result.output.contains("got it")
    }

    def "can construct a task with @Inject services and constructor args"() {
        given:
        buildFile << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            import org.gradle.workers.WorkerExecutor
            import javax.inject.Inject

            open class CustomTask @Inject constructor(private val number: Int, private val executor: WorkerExecutor) : DefaultTask() {
                @TaskAction fun run() = println(if (executor != null) "got it \$number" else "\$number NOT IT")
            }

            tasks.create("myTask", CustomTask::class.java, 15)
        """

        when:
        run 'myTask'

        then:
        result.output.contains("got it 15")
    }

    @Ignore
    def "can construct a task with @Inject services and constructor args via extension"() {
        given:
        buildFile << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            import org.gradle.workers.WorkerExecutor
            import javax.inject.Inject

            open class CustomTask @Inject constructor(private val number: Int, private val executor: WorkerExecutor) : DefaultTask() {
                @TaskAction fun run() = println(if (executor != null) "got it \$number" else "\$number NOT IT")
            }

            tasks<CustomTask>("myTask", 15)
        """

        when:
        run 'myTask'

        then:
        result.output.contains("got it 15")
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

        cleanup: // wait for all daemons to shutdown so the test dir can be deleted
        executer.cleanup()
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
}
