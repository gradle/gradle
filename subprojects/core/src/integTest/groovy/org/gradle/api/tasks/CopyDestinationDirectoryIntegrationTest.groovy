/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.modes.UnsupportedWithConfigurationCache
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/25824")
class CopyDestinationDirectoryIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file("src/a.txt") << "a"
    }

    def "#task can configure the destination lazily through destinationDirectory provider"() {
        buildFile """
            task copy(type: $task) {
                from 'src'
                destinationDirectory = layout.buildDirectory.dir("out")
            }
        """

        when:
        run 'copy'

        then:
        file('build/out/a.txt').text == 'a'

        where:
        task << ['Copy', 'Sync']
    }

    def "#task destinationDirectory reflects a destination set through into()"() {
        buildFile """
            task copy(type: $task) {
                from 'src'
                into layout.buildDirectory.dir("viaInto")
            }
            task checkDestination {
                def actual = copy.destinationDirectory.locationOnly
                def legacy = provider { copy.destinationDir }
                def expected = layout.buildDirectory.dir("viaInto")
                doLast {
                    assert actual.get().asFile == expected.get().asFile
                    assert legacy.get() == expected.get().asFile
                }
            }
        """

        expect:
        run 'checkDestination'

        where:
        task << ['Copy', 'Sync']
    }

    def "#task destinationDir setter is reflected in destinationDirectory provider"() {
        buildFile """
            task copy(type: $task) {
                from 'src'
                destinationDir = file("\$buildDir/legacy")
            }
            task checkDestination {
                def actual = copy.destinationDirectory.locationOnly
                def expected = layout.buildDirectory.dir("legacy")
                doLast {
                    assert actual.get().asFile == expected.get().asFile
                }
            }
        """

        expect:
        run 'checkDestination'

        where:
        task << ['Copy', 'Sync']
    }

    def "wiring #task destinationDirectory as another task input carries the task dependency"() {
        buildFile """
            task producer(type: $task) {
                from 'src'
                destinationDirectory = layout.buildDirectory.dir("produced")
            }
            task consumer(type: Copy) {
                from producer.destinationDirectory
                into layout.buildDirectory.dir("consumed")
            }
        """

        when:
        run 'consumer'

        then:
        result.assertTasksExecuted(':producer', ':consumer')
        file('build/consumed/a.txt').text == 'a'

        where:
        task << ['Copy', 'Sync']
    }

    def "#task legacy convention mapping on rootSpec destinationDir is routed to destinationDirectory"() {
        buildFile """
            task copy(type: $task) {
                from 'src'
            }
            copy.rootSpec.conventionMapping.map('destinationDir') { file("\$buildDir/mapped") }
        """

        when:
        run 'copy'

        then:
        file('build/mapped/a.txt').text == 'a'

        where:
        task << ['Copy', 'Sync']
    }

    @UnsupportedWithConfigurationCache(because = "configuration cache only supports convention mapping for task fields with matching names")
    def "#task legacy convention mapping on task destinationDir remains supported"() {
        buildFile """
            task copy(type: $task) {
                from 'src'
            }
            copy.conventionMapping.map('destinationDir') { file("\$buildDir/mapped") }
        """

        when:
        run 'copy'

        then:
        file('build/mapped/a.txt').text == 'a'

        where:
        task << ['Copy', 'Sync']
    }

    def "wiring #task destinationDirectory as another task input works with a legacy into() destination"() {
        buildFile """
            task producer(type: $task) {
                from 'src'
                into "\$buildDir/produced"
            }
            task consumer(type: Copy) {
                from producer.destinationDirectory
                into layout.buildDirectory.dir("consumed")
            }
        """

        when:
        run 'consumer'

        then:
        result.assertTasksExecuted(':producer', ':consumer')
        file('build/consumed/a.txt').text == 'a'

        where:
        task << ['Copy', 'Sync']
    }

    @Requires(value = TestExecutionPreconditions.NotConfigCached, reason = "handles CC explicitly in the test")
    def "#task destinationDirectory survives the configuration cache"() {
        buildFile """
            task copy(type: $task) {
                from 'src'
                destinationDirectory = layout.buildDirectory.dir("out")
            }
        """

        when:
        run 'copy', '--configuration-cache'

        then:
        file('build/out/a.txt').text == 'a'

        when:
        file('build/out').deleteDir()
        run 'copy', '--configuration-cache'

        then:
        outputContains('Reusing configuration cache.')
        file('build/out/a.txt').text == 'a'

        where:
        task << ['Copy', 'Sync']
    }

    def "task action can #operation into a directory derived from the task's own output directory"() {
        buildFile """
            abstract class CustomTask extends DefaultTask {
                @InputDirectory abstract DirectoryProperty getSource()
                @OutputDirectory abstract DirectoryProperty getOutputDirectory()
                @Inject abstract FileSystemOperations getFs()
                @TaskAction void go() {
                    fs.$operation {
                        from(source)
                        into(outputDirectory.dir("nested"))
                    }
                }
            }
            tasks.register("custom", CustomTask) {
                source = layout.projectDirectory.dir("src")
                outputDirectory = layout.buildDirectory.dir("out")
            }
        """

        when:
        run 'custom'

        then:
        file('build/out/nested/a.txt').text == 'a'

        where:
        operation << ['copy', 'sync']
    }

    def "#task subclass can wire its own directory property through into() in its constructor"() {
        buildFile """
            abstract class CustomCopy extends $task {
                @Internal abstract DirectoryProperty getDefaultDestinationDirectory()
                CustomCopy() {
                    into(defaultDestinationDirectory.dir("plugins"))
                }
            }
            def copy = tasks.register("copy", CustomCopy) {
                from 'src'
                defaultDestinationDirectory = layout.buildDirectory.dir("sandbox")
            }
            tasks.register("checkDestination") {
                def actual = copy.flatMap { it.destinationDirectory.locationOnly }
                def legacy = copy.map { it.destinationDir }
                doLast {
                    println "destinationDirectory: " + actual.get().asFile
                    println "destinationDir: " + legacy.get()
                }
            }
        """

        when:
        run 'copy', 'checkDestination'

        then:
        file('build/sandbox/plugins/a.txt').text == 'a'
        outputContains("destinationDirectory: " + file('build/sandbox/plugins'))
        outputContains("destinationDir: " + file('build/sandbox/plugins'))

        where:
        task << ['Copy', 'Sync']
    }

    def "#task subclass can continue to provide its destination by overriding destinationDir"() {
        buildFile """
            abstract class CustomCopy extends $task {
                @Internal abstract DirectoryProperty getDefaultDestinationDirectory()

                @Override
                File getDestinationDir() {
                    return defaultDestinationDirectory.get().asFile
                }
            }
            tasks.register("copy", CustomCopy) {
                from 'src'
                defaultDestinationDirectory = layout.buildDirectory.dir("sandbox")
            }
        """

        when:
        run 'copy'

        then:
        file('build/sandbox/a.txt').text == 'a'

        when:
        run 'copy'

        then:
        result.assertTaskSkipped(':copy')

        where:
        task << ['Copy', 'Sync']
    }

    def "#task subclass action can read destinationDir when the destination derives from its own output property"() {
        buildFile """
            abstract class CustomCopy extends $task {
                @OutputDirectory abstract DirectoryProperty getSandboxDirectory()
                @Inject abstract FileSystemOperations getFs()
                CustomCopy() {
                    into(sandboxDirectory.dir("plugins"))
                }
                @Override
                protected void copy() {
                    super.copy()
                    println "destinationDir: " + destinationDir
                    println "destinationDirectory: " + destinationDirectory.get().asFile
                    fs.copy {
                        from(destinationDir)
                        into(sandboxDirectory.dir("plugins-copy"))
                    }
                }
            }
            tasks.register("copy", CustomCopy) {
                from 'src'
                sandboxDirectory = layout.buildDirectory.dir("sandbox")
            }
        """

        when:
        run 'copy'

        then:
        file('build/sandbox/plugins/a.txt').text == 'a'
        file('build/sandbox/plugins-copy/a.txt').text == 'a'
        outputContains("destinationDir: " + file('build/sandbox/plugins'))
        outputContains("destinationDirectory: " + file('build/sandbox/plugins'))

        where:
        task << ['Copy', 'Sync']
    }
}
