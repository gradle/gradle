/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.configurationcache.serialization.codecs.ClosureCodec

class ConfigurationCacheGroovyClosureIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "from-cache build fails when task action closure reads a project property"() {
        given:
        buildFile << """
            tasks.register("some") {
                doFirst {
                    println(name) // task property is ok
                    println($expression)
                }
            }
        """

        when:
        configurationCacheFails ":some"

        then:
        failure.assertHasFileName("Build file '$buildFile'")
        failure.assertHasLineNumber(5)
        failure.assertHasFailure("Execution failed for task ':some'.") {
            it.assertHasCause("Cannot reference a Gradle script object from a Groovy closure as these are not supported with the configuration cache.")
        }

        where:
        expression       | _
        "buildDir"       | _
        "this.buildDir"  | _
        "owner.buildDir" | _
    }

    def "from-cache build fails when task action closure sets a project property"() {
        given:
        buildFile << """
            tasks.register("some") {
                doFirst {
                    description = "broken" // task property is ok
                    $expression = 1.2
                }
            }
        """

        when:
        configurationCacheFails ":some"

        then:
        failure.assertHasFileName("Build file '$buildFile'")
        failure.assertHasLineNumber(5)
        failure.assertHasFailure("Execution failed for task ':some'.") {
            it.assertHasCause("Cannot reference a Gradle script object from a Groovy closure as these are not supported with the configuration cache.")
        }

        where:
        expression      | _
        "version"       | _
        "this.version"  | _
        "owner.version" | _
    }

    def "from-cache build fails when task action closure invokes a project method"() {
        given:
        buildFile << """
            tasks.register("some") {
                doFirst {
                    println(file("broken"))
                }
            }
        """

        when:
        configurationCacheFails ":some"

        then:
        failure.assertHasFileName("Build file '$buildFile'")
        failure.assertHasLineNumber(4)
        failure.assertHasFailure("Execution failed for task ':some'.") {
            it.assertHasCause("Cannot reference a Gradle script object from a Groovy closure as these are not supported with the configuration cache.")
        }
    }

    def "from-cache build fails when task action nested closure reads a project property"() {
        given:
        buildFile << """
            tasks.register("some") {
                doFirst {
                    def cl = {
                        println(name) // task property is ok
                        println(buildDir)
                    }
                    cl()
                }
            }
        """

        when:
        configurationCacheFails ":some"

        then:
        failure.assertHasFileName("Build file '$buildFile'")
        failure.assertHasLineNumber(6)
        failure.assertHasFailure("Execution failed for task ':some'.") {
            it.assertHasCause("Cannot reference a Gradle script object from a Groovy closure as these are not supported with the configuration cache.")
        }
    }

    def "from-cache build fails when task action defined in settings script reads a settings property"() {
        given:
        settingsFile << """
            gradle.rootProject {
                tasks.register("some") {
                    doFirst {
                        println(name) // task property is ok
                        println(rootProject)
                    }
                }
            }
        """

        when:
        configurationCacheFails ":some"

        then:
        failure.assertHasFileName("Settings file '$settingsFile'")
        failure.assertHasLineNumber(6)
        failure.assertHasFailure("Execution failed for task ':some'.") {
            it.assertHasCause("Cannot reference a Gradle script object from a Groovy closure as these are not supported with the configuration cache.")
        }
    }

    def "from-cache build fails when task action defined in init script reads a `Gradle` property"() {
        given:
        def initScript = file("init.gradle")
        initScript << """
            rootProject {
                tasks.register("some") {
                    doFirst {
                        println(name) // task property is ok
                        println(gradleVersion)
                    }
                }
            }
        """
        executer.beforeExecute { withArguments("-I", initScript.absolutePath) }

        when:
        configurationCacheFails ":some"

        then:
        failure.assertHasFileName("Initialization script '$initScript'")
        failure.assertHasLineNumber(6)
        failure.assertHasFailure("Execution failed for task ':some'.") {
            it.assertHasCause("Cannot reference a Gradle script object from a Groovy closure as these are not supported with the configuration cache.")
        }
    }

    def "from-cache build fails when task onlyIf closure reads a project property"() {
        given:
        buildFile << """
            tasks.register("some") {
                onlyIf { t ->
                    println(t.name) // task property is ok
                    println(buildDir)
                    true
                }
                doFirst {
                }
            }
        """

        when:
        configurationCacheFails ":some"

        then:
        failure.assertHasFileName("Build file '$buildFile'")
        failure.assertHasLineNumber(5)
        failure.assertHasFailure("Could not evaluate onlyIf predicate for task ':some'.") {
            // The cause is not reported
        }
    }

    def "discards implicit objects for Groovy closure"() {
        given:
        createDir("buildSrc") {
            file("src/main/groovy/GroovyTask.groovy") << """
                import ${DefaultTask.name}
                import ${Internal.name}
                import ${TaskAction.name}

                class GroovyTask extends DefaultTask {
                    @Internal
                    List<Closure> actions = []

                    void action() {
                        actions.add { "Groovy closure in task with delegate=\$delegate, owner=\${owner.class.name}, this=\${this.class.name}" }
                    }

                    void actionWithDelegate() {
                        def action = { "Groovy closure in task with custom delegate=\$delegate, owner=\${owner.class.name}, this=\${this.class.name}" }
                        action.delegate = new Bean()
                        actions.add(action)
                    }

                    void actionWithChainedDelegate() {
                        def cl = {
                            delegate = new Bean()
                            actions.add { "nested Groovy closure in task with delegate=\$delegate, owner=\${owner.class.name}, this=\${this.class.name}" }
                        }
                        cl()
                    }

                    @TaskAction
                    void run() {
                        actions.forEach { action ->
                            println action()
                        }
                    }
                }
                class Bean {
                    final String source = "custom delegate"
                }
            """
        }
        buildFile """
            task test(type: GroovyTask) {
                action()
                actionWithDelegate()
                actionWithChainedDelegate()
                actions.add { "Groovy closure in script with delegate=\$delegate, owner=\${owner.class.name}, this=\${this.class.name}" }
                def action = { "Groovy closure in script with custom delegate=\$delegate, owner=\${owner.class.name}, this=\${this.class.name}" }
                action.delegate = new Bean()
                actions.add(action)
            }
        """

        expect:
        2.times {
            configurationCacheRun("test")
            def brokenObject = ClosureCodec.BrokenObject.name
            def brokenScript = ClosureCodec.BrokenScript.name
            outputContains("Groovy closure in task with delegate=null, owner=$brokenObject, this=$brokenObject")
            outputContains("Groovy closure in task with custom delegate=null, owner=$brokenObject, this=$brokenObject")
            outputContains("nested Groovy closure in task with delegate=null, owner=$brokenObject, this=$brokenObject")
            outputContains("Groovy closure in script with delegate=null, owner=$brokenScript, this=$brokenScript")
            outputContains("Groovy closure in script with custom delegate=null, owner=$brokenScript, this=$brokenScript")
        }
    }
}
