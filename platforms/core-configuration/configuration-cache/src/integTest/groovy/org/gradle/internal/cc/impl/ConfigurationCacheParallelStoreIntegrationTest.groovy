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

package org.gradle.internal.cc.impl

import org.gradle.initialization.StartParameterBuildOptions

class ConfigurationCacheParallelStoreIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "failures while storing different projects are reported"() {
        given:
        def projects = (1..5).collect  {"proj$it" }
        def configurationCache = newConfigurationCacheFixture()
        settingsFile << """
            include ${projects.collect {"'${it}'" }.join(',')}
        """

        def createBuildFile = {
            file(buildFile.name) << """
                task t {
                    outputs.file({ -> throw new RuntimeException("\${ project.identityPath } went BOOM!") })
                }
            """
        }

        projects.each {
            createDir(it, createBuildFile)
        }

        when:
        configurationCacheFails("t")

        then:
        configurationCache.assertStateStoreFailed()
        outputContains("Configuration cache entry discarded due to serialization error.")
        failure.assertHasFailures(1)
        // we cannot assert on the build script absolute path as it is non-deterministic
        failure.assertHasLineNumber(3)
        failure.assertHasDescription("Error while saving task graph")
        projects.each {
            failure.assertHasCause("Exception while storing configuration for :$it: :$it went BOOM!")
        }
    }

    def "lazy tasks of a project referenced by tasks of other projects can be realized while storing in parallel"() {
        given:
        def consumers = (1..30).collect { "consumer$it" }
        def configurationCache = newConfigurationCacheFixture()
        settingsFile << """
            include 'lib', ${consumers.collect { "'${it}'" }.join(',')}
        """
        consumers.each { file(it).createDir() }
        file("lib/build.gradle") << """
            (1..50).each { i ->
                tasks.register("libTask\$i") {
                    outputs.file(layout.buildDirectory.file("libTask\${i}.txt"))
                }
            }
        """
        buildFile << """
            abstract class ConsumerTask extends DefaultTask {
                // Not an input, so that the lib tasks are not realized while the task graph is calculated
                @Internal
                abstract ConfigurableFileCollection getLibOutputs()

                @TaskAction
                void run() {}
            }

            subprojects {
                if (name.startsWith('consumer')) {
                    evaluationDependsOn(':lib')
                    def lib = project(':lib')
                    tasks.register('consume', ConsumerTask) {
                        (1..50).each { i ->
                            libOutputs.from(lib.tasks.named("libTask\$i").map { it.outputs.files })
                        }
                    }
                }
            }
        """

        when:
        configurationCacheRun("--parallel", "consume")

        then:
        configurationCache.assertStateStored()
    }

    def "lazy tasks of projects referencing each other can realize tasks of other projects while storing in parallel"() {
        given:
        def projects = (1..30).collect { "proj$it" }
        def configurationCache = newConfigurationCacheFixture()
        settingsFile << """
            include 'shared', ${projects.collect { "'${it}'" }.join(',')}
        """
        (projects + "shared").each { file(it).createDir() }
        buildFile << """
            abstract class ConsumerTask extends DefaultTask {
                // Not an input, so that the referenced tasks are not realized while the task graph is calculated
                @Internal
                abstract ConfigurableFileCollection getOtherOutputs()

                @TaskAction
                void run() {}
            }

            project(':shared') {
                (1..50).each { i ->
                    tasks.register("sharedTask\$i")
                }
            }

            def projectNames = [${projects.collect { "'${it}'" }.join(',')}]
            subprojects {
                if (name.startsWith('proj')) {
                    evaluationDependsOn(':shared')
                    def index = projectNames.indexOf(name)
                    def neighbours = [projectNames[(index + 1) % projectNames.size()], projectNames[(index + projectNames.size() - 1) % projectNames.size()]]
                    (1..50).each { i ->
                        tasks.register("lazyTask\$i") {
                            // Realizes a task of a third project while this task is realized on behalf of another project
                            dependsOn(project(':shared').tasks.named("sharedTask\$i").get())
                            outputs.file(layout.buildDirectory.file("lazyTask\${i}.txt"))
                        }
                    }
                    tasks.register('consume', ConsumerTask) {
                        neighbours.each { neighbour ->
                            (1..50).each { i ->
                                otherOutputs.from(project(":\$neighbour").tasks.named("lazyTask\$i").map { it.outputs.files })
                            }
                        }
                    }
                }
            }
        """

        when:
        configurationCacheRun("--parallel", "consume")

        then:
        configurationCache.assertStateStored()
    }

    def "lazy elements of #container of a project referenced by tasks of other projects can be realized while storing in parallel"() {
        given:
        def consumers = (1..30).collect { "consumer$it" }
        def configurationCache = newConfigurationCacheFixture()
        settingsFile << """
            include 'lib', ${consumers.collect { "'${it}'" }.join(',')}
        """
        (consumers + "lib").each { file(it).createDir() }
        buildFile << """
            abstract class Item implements Named {
                private final String name

                @javax.inject.Inject
                Item(String name) {
                    this.name = name
                }

                String getName() {
                    name
                }

                abstract RegularFileProperty getFile()
            }

            abstract class ConsumerTask extends DefaultTask {
                // Not an input, so that the elements are not realized while the task graph is calculated
                @Internal
                abstract ConfigurableFileCollection getLibOutputs()

                @TaskAction
                void run() {}
            }

            project(':lib') {
                $libSetup
            }

            subprojects {
                if (name.startsWith('consumer')) {
                    evaluationDependsOn(':lib')
                    def lib = project(':lib')
                    tasks.register('consume', ConsumerTask) {
                        (1..50).each { i ->
                            libOutputs.from($consumerReference)
                        }
                    }
                }
            }
        """

        when:
        configurationCacheRun("--parallel", "consume")

        then:
        configurationCache.assertStateStored()

        where:
        container                                       | libSetup                                                                                                                                                                                                                                        | consumerReference
        "a container created by the object factory"     | "def items = objects.domainObjectContainer(Item); (1..50).each { i -> items.register(\"item\$i\") { file.set(layout.buildDirectory.file(\"item\${i}.txt\")) } }; extensions.add('items', items)"                                          | "lib.extensions.getByName('items').named(\"item\$i\").flatMap { it.file }"
        "a polymorphic container"                       | "def items = objects.polymorphicDomainObjectContainer(Item); items.registerFactory(Item) { objects.newInstance(Item, it) }; (1..50).each { i -> items.register(\"item\$i\", Item) { file.set(layout.buildDirectory.file(\"item\${i}.txt\")) } }; extensions.add('items', items)" | "lib.extensions.getByName('items').named(\"item\$i\").flatMap { it.file }"
    }

    def "parallel store is disabled by default"() {
        given:
        settingsFile.createFile()

        when:
        run(ENABLE_CLI_OPT, "help", "-d")

        then:
        outputDoesNotContain("Parallel Configuration Cache is an incubating feature.")
        // even if the feature is not enabled, only storing is done sequentially
        output.contains("[org.gradle.configurationcache] saving task graph sequentially")
        // loading is still parallel (unless explicitly disabled via internal property)
        output.contains("[org.gradle.configurationcache] reading task graph in parallel")
    }

    def "parallel CC may be opted in"() {
        given:
        settingsFile.createFile()

        when:
        run(ENABLE_CLI_OPT, ENABLE_PARALLEL_CACHE, "help")

        then:
        output.contains("Parallel Configuration Cache is an incubating feature.")
    }

    def "parallel store may be opted out"() {
        given:
        settingsFile.createFile()

        when:
        configurationCacheRun("help", "-d", "-Dorg.gradle.internal.configuration-cache.parallel-store=false")

        then:
        output.contains("[org.gradle.configurationcache] saving task graph sequentially")
        output.contains("[org.gradle.configurationcache] reading task graph in parallel")
    }

    def "parallel load may be opted out"() {
        given:
        settingsFile.createFile()

        when:
        configurationCacheRun("help", "-d", "-Dorg.gradle.internal.configuration-cache.parallel-load=false")

        then:
        output.contains("[org.gradle.configurationcache] saving task graph in parallel")
        output.contains("[org.gradle.configurationcache] reading task graph sequentially")
    }

    def "parallel store is enabled by IP"() {
        given:
        settingsFile.createFile()

        when:
        run("-D${StartParameterBuildOptions.IsolatedProjectsOption.PROPERTY_NAME}=true", "help", "-d")

        then:
        outputDoesNotContain("Parallel Configuration Cache is an incubating feature.")
        output.contains("[org.gradle.configurationcache] saving task graph in parallel")
        output.contains("[org.gradle.configurationcache] reading task graph in parallel")
    }

    def "parallel store is enabled by IP but can be opted out"() {
        given:
        settingsFile.createFile()

        when:
        run("-D${StartParameterBuildOptions.IsolatedProjectsOption.PROPERTY_NAME}=true", "help", "-d",
            "-Dorg.gradle.internal.configuration-cache.parallel-store=false")

        then:
        outputDoesNotContain("Parallel Configuration Cache is an incubating feature.")
        output.contains("[org.gradle.configurationcache] saving task graph sequentially")
        output.contains("[org.gradle.configurationcache] reading task graph in parallel")
    }

}
