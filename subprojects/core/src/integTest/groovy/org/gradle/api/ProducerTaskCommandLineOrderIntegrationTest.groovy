/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api

import org.gradle.api.internal.artifacts.transform.UnzipTransform
import org.gradle.integtests.fixtures.ToBeFixedForIsolatedProjects
import org.gradle.integtests.fixtures.executer.TaskOrderSpecs
import spock.lang.Issue

class ProducerTaskCommandLineOrderIntegrationTest extends AbstractCommandLineOrderTaskIntegrationTest {
    def "producer task with a dependency in another project will run before destroyer tasks when ordered first (type: #type)"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar')
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').produces('build/foo', type)
        def generateBar = bar.task('generateBar').produces('build/bar', type).dependsOn(generateFoo)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(generate.path, clean.path)

            result.assertTaskOrder(generateFoo.fullPath, generateBar.fullPath, generate.fullPath)
            result.assertTaskOrder(generateFoo.fullPath, cleanFoo.fullPath, clean.fullPath)
            result.assertTaskOrder(generateBar.fullPath, cleanBar.fullPath, clean.fullPath)
        }

        where:
        type << ProductionType.values()
    }

    def "producer task with a dependency in another build will run before destroyer tasks when ordered first (type: #type)"() {
        def foo = includedBuild('child').subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar')
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').produces('build/foo', type)
        def generateBar = bar.task('generateBar').produces('build/bar', type).dependsOn(generateFoo)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(generate.path, clean.path)

            result.assertTaskOrder(generateFoo.fullPath, generateBar.fullPath, generate.fullPath)
            result.assertTaskOrder(generateFoo.fullPath, cleanFoo.fullPath, clean.fullPath)
            result.assertTaskOrder(generateBar.fullPath, cleanBar.fullPath, clean.fullPath)
        }

        where:
        type << ProductionType.values()
    }

    // Because we prevent destroyers from running in between producers and consumers, this does not currently work the way a user would
    // expect.  The intermediate destroyer task will be delayed until the consumers have all run.  If we were looking at the specific
    // outputs that a consumer required, rather than just the dependency relationships, we could support this case.
    // Currently flaky with CC enabled
    def "producer task followed by a destroyer task followed by a producer with a dependency on the first producer are run in the correct order"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFooLocal = foo.task('cleanFooLocalState').destroys('build/foo-local')
        def generateFoo = foo.task('generateFoo').outputs('build/foo').localState('build/foo-local')
        def generateBar = bar.task('generateBar').outputs('build/bar').dependsOn(generateFoo)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)
        def dist = rootBuild.task('dist').outputs('build/dist').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(generate.path, cleanFooLocal.path, dist.path)

            // The user expects:
            // result.assertTaskOrder(generateFoo.fullPath, generateBar.fullPath, TaskOrderSpecs.any(generate.fullPath, cleanFooLocal.fullPath), dist.fullPath)
            result.assertTaskOrder(generateFoo.fullPath, generateBar.fullPath, TaskOrderSpecs.any(generate.fullPath, dist.fullPath), cleanFooLocal.fullPath)
        }
    }

    def "producer task with a dependency in another project followed by a destroyer task followed by a producer are run in the correct order"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanBar = bar.task('cleanBar').destroys('build/bar').destroys("build/pkg-src")
        def generateFoo = foo.task('generateFoo').outputs('build/foo').localState('build/foo-local')
        def generateBar = bar.task('generateBar').outputs('build/bar').dependsOn(generateFoo)
        def packageBarSources = bar.task('packageBarSources').outputs('build/pkg-src')

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(generateBar.path, cleanBar.path, packageBarSources.path)

            result.assertTaskOrder(generateFoo.fullPath, generateBar.fullPath, cleanBar.fullPath, packageBarSources.fullPath)
        }
    }

    def "producer task with a dependency in another build followed by a destroyer task followed by a producer are run in the correct order"() {
        def foo = includedBuild('child').subproject(':foo')
        def bar = subproject(':bar')

        def cleanBar = bar.task('cleanBar').destroys('build/bar').destroys("build/pkg-src")
        def generateFoo = foo.task('generateFoo').outputs('build/foo').localState('build/foo-local')
        def generateBar = bar.task('generateBar').outputs('build/bar').dependsOn(generateFoo)
        def packageBarSources = bar.task('packageBarSources').outputs('build/pkg-src')

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(generateBar.path, cleanBar.path, packageBarSources.path)

            result.assertTaskOrder(generateFoo.fullPath, generateBar.fullPath, cleanBar.fullPath, packageBarSources.fullPath)
        }
    }

    def "multiple producer tasks listed on the command line followed by a destroyer can run concurrently and are executed in the correct order"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def generateFoo = foo.task('generateFoo').outputs('build/foo').shouldBlock()
        def generateBar = bar.task('generateBar').outputs('build/bar').dependsOn(generateFoo)
        def packageBarSources = bar.task('packageBarSources').outputs('build/pkg-src').shouldBlock()
        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar').destroys('build/pkg-src')
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)

        server.start()
        server.expectConcurrent(generateFoo.path, packageBarSources.path)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(generateBar.path, packageBarSources.path, clean.path)

            result.assertTaskOrder(generateFoo.fullPath, generateBar.fullPath)
            result.assertTaskOrder(generateFoo.fullPath, cleanFoo.fullPath, clean.fullPath)
            result.assertTaskOrder(packageBarSources.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(generateBar.fullPath, cleanBar.fullPath, clean.fullPath)
        }
    }

    def "producer task finalized by a task in another project will run before destroyer tasks when ordered first"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar')
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateBar = bar.task('generateBar').outputs('build/bar')
        def generateFoo = foo.task('generateFoo').outputs('build/foo').finalizedBy(generateBar)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(generate.path, clean.path)

            result.assertTaskOrder(generateFoo.fullPath, generateBar.fullPath, generate.fullPath)
            result.assertTaskOrder(generateFoo.fullPath, cleanFoo.fullPath, clean.fullPath)
            result.assertTaskOrder(generateBar.fullPath, cleanBar.fullPath, clean.fullPath)
        }
    }

    def "producer task finalizing both a producer and a destroyer will run after destroyer tasks"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def generateBar = bar.task('generateBar').outputs('build/bar')
        def generateFoo = foo.task('generateFoo').outputs('build/foo').finalizedBy(generateBar)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)
        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar').finalizedBy(generateBar)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(generate.path, clean.path)

            result.assertTaskOrder(generateFoo.fullPath, generateBar.fullPath, generate.fullPath)
            result.assertTaskOrder(generateFoo.fullPath, cleanFoo.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, TaskOrderSpecs.any(generateBar.fullPath, clean.fullPath))
        }
    }

    def "a task that is neither a producer nor a destroyer can run concurrently with producer tasks"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def generateFoo = foo.task('generateFoo').outputs('build/foo').shouldBlock()
        def exec = bar.task('exec').shouldBlock()

        server.start()

        writeAllFiles()

        expect:
        2.times {
            server.expectConcurrent(generateFoo.path, exec.path)

            args '--parallel', '--max-workers=2', '--rerun-tasks' // add --rerun-tasks so that tasks are not up-to-date on second invocation
            succeeds(generateFoo.path, exec.path)
        }
    }

    def "producer task that mustRunAfter a task in another project will run before destroyer tasks when ordered first"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar')
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').outputs('build/foo')
        def generateBar = bar.task('generateBar').outputs('build/bar').mustRunAfter(generateFoo)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(generate.path, clean.path)

            result.assertTaskOrder(generateFoo.fullPath, generateBar.fullPath, generate.fullPath)
            result.assertTaskOrder(generateFoo.fullPath, cleanFoo.fullPath, clean.fullPath)
            result.assertTaskOrder(generateBar.fullPath, cleanBar.fullPath, clean.fullPath)
        }
    }

    @ToBeFixedForIsolatedProjects(because = "Property dynamic lookup")
    def "producer task with a dependency on an artifact transform will run before destroyer tasks when ordered first"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar')
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').outputs('build/foo').inputFiles("configurations.unzipped")
        def generateBar = bar.task('generateBar').outputs('build/bar')
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()
        foo.buildFile << """
            ${artifactTransformConfiguration}
            dependencies {
                unzipped project(path: '${bar.path}', configuration: 'zipped')
            }
        """
        bar.buildFile << """
            ${zipTaskConfiguration}
        """
        bar.projectDir.createDir('inputFiles').createFile('bar.txt')

        when:
        args '--parallel', '--max-workers=2'
        succeeds(generate.path, clean.path)

        then:
        result.assertTaskOrder(TaskOrderSpecs.any(generateBar.fullPath, ':bar:zip'), generateFoo.fullPath, generate.fullPath)
        result.assertTaskOrder(generateFoo.fullPath, cleanFoo.fullPath, clean.fullPath)
        result.assertTaskOrder(generateBar.fullPath, cleanBar.fullPath, clean.fullPath)

        and:
        outputContains("Executing unzip transform...")
    }

    @Issue("https://github.com/gradle/gradle/issues/20195")
    def "producer task that finalizes a destroyer task will run after the destroyer even when ordered first (type: #type)"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar')
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').produces('build/foo', type)
        def generateBar = bar.task('generateBar').produces('build/bar', type)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)
        cleanBar.finalizedBy(generateBar)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(generate.path, clean.path)

            result.assertTaskOrder(generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(generateFoo.fullPath, cleanFoo.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, clean.fullPath)
        }

        where:
        type << ProductionType.values()
    }

    @Issue("https://github.com/gradle/gradle/issues/20195")
    def "producer task that finalizes a destroyer task and is also a dependency will run after the destroyer even when ordered first (type: #type)"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar')
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateBar = bar.task('generateBar').produces('build/bar', type)
        def generateFoo = foo.task('generateFoo').produces('build/foo', type).dependsOn(generateBar)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)
        cleanBar.finalizedBy(generateBar)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(generate.path, clean.path)

            result.assertTaskOrder(generateBar.fullPath, generateFoo.fullPath, generate.fullPath)
            // generateBar must run after cleanBar, as generateBar finalizes cleanBar
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, clean.fullPath)
        }

        where:
        type << ProductionType.values()
    }

    @Issue("https://github.com/gradle/gradle/issues/20272")
    def "producer task that mustRunAfter a task that does not get executed will run before destroyer tasks when ordered first (type: #type)"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar')
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateSpecialBar = bar.task('generateSpecialBar')
        def generateFoo = foo.task('generateFoo').produces('build/foo', type)
        def generateBar = bar.task('generateBar').produces('build/bar', type).dependsOn(generateFoo).mustRunAfter(generateSpecialBar)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(generate.path, clean.path)

            result.assertTaskOrder(generateFoo.fullPath, generateBar.fullPath, generate.fullPath)
            result.assertTaskOrder(generateFoo.fullPath, cleanFoo.fullPath, clean.fullPath)
            result.assertTaskOrder(generateBar.fullPath, cleanBar.fullPath, clean.fullPath)
        }

        where:
        type << ProductionType.values()
    }

    static String getArtifactTransformConfiguration() {
        return """
            def artifactType = Attribute.of('artifactType', String)
            def compressed = Attribute.of('compressed', Boolean)

            dependencies {
                attributesSchema {
                    attribute(compressed)
                }
                artifactTypes {
                    zip {
                        attributes.attribute(compressed, true)
                    }
                }
            }

            abstract class TestUnzipTransform extends ${UnzipTransform.class.name} {
                @Override
                public void transform(TransformOutputs outputs) {
                    println "Executing unzip transform..."
                    super.transform(outputs)
                }
            }

            dependencies {
                registerTransform(TestUnzipTransform) {
                    from.attribute(artifactType, "zip").attribute(compressed, true)
                    to.attribute(artifactType, "directory").attribute(compressed, false)
                }
            }

            configurations {
                unzipped {
                    attributes {
                        attribute(compressed, false)
                    }
                }
            }
        """
    }

    static String getZipTaskConfiguration() {
        return """
            apply plugin: 'base'

            configurations {
                zipped
            }

            task zip(type: Zip) {
                from('inputFiles')
            }

            artifacts {
                zipped zip
            }
        """
    }
}
