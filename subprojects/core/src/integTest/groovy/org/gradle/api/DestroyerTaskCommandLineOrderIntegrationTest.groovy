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

import org.gradle.integtests.fixtures.executer.TaskOrderSpecs
import spock.lang.Issue

class DestroyerTaskCommandLineOrderIntegrationTest extends AbstractCommandLineOrderTaskIntegrationTest {
    def "destroyer task with a dependency in another project will run before producer tasks when ordered first (type: #type)"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar').dependsOn(cleanFoo)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').produces('build/foo', type)
        def generateBar = bar.task('generateBar').produces('build/bar', type)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
        }

        where:
        type << ProductionType.values()
    }

    def "a producer task will not run before a task in another project that destroys what it produces (type: #type)"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = foo.task('cleanBar').destroys('../bar/build/bar').dependsOn(cleanFoo)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').produces('build/foo', type)
        def generateBar = bar.task('generateBar').produces('build/bar', type)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
        }

        where:
        type << ProductionType.values()
    }

    def "destroyer task with a dependency in another build will run before producer tasks when ordered first (type: #type)"() {
        def foo = includedBuild('child').subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar').dependsOn(cleanFoo)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').produces('build/foo', type)
        def generateBar = bar.task('generateBar').produces('build/bar', type)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
        }

        where:
        type << ProductionType.values()
    }

    def "allows explicit task dependencies to override command line order"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar').dependsOn(cleanFoo)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').outputs('build/foo')
        def generateBar = bar.task('generateBar').outputs('build/bar')
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        // conflicts with command line order
        cleanBar.dependsOn(generateBar)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(generateBar.fullPath, cleanBar.fullPath)
        }
    }

    def "allows command line order to override shouldRunAfter relationship"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar').dependsOn(cleanFoo)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').outputs('build/foo')
        def generateBar = bar.task('generateBar').outputs('build/bar')
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        // conflicts with command line order
        cleanBar.shouldRunAfter(generateBar)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath)
        }
    }

    def "destroyer task with a dependency in another project followed by a producer task followed by a destroyer task are run in the correct order"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar').dependsOn(cleanFoo)
        def cleanFooLocal = foo.task('cleanFooLocalState').destroys('build/foo-local')
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').outputs('build/foo').localState('build/foo-local')
        def generateBar = bar.task('generateBar').outputs('build/bar')
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path, cleanFooLocal.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, TaskOrderSpecs.any(generate.fullPath, cleanFooLocal.fullPath))
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
        }
    }

    def "destroyer task with a dependency in another build followed by a producer task followed by a destroyer task are run in the correct order"() {
        def foo = includedBuild('child').subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar').dependsOn(cleanFoo)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def cleanFooLocal = foo.task('cleanFooLocalState').destroys('build/foo-local')
        def cleanLocal = rootBuild.task('cleanLocal').dependsOn(cleanFooLocal)
        def generateFoo = foo.task('generateFoo').outputs('build/foo').localState('build/foo-local')
        def generateBar = bar.task('generateBar').outputs('build/bar')
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        // TODO - does not work with CC yet. cleanFoo starts, which blocks generateFoo. However, cleanFooLocalState can start and so runs before generateFoo
        // However, the cleanFooLocalState should block because it conflicts with generateFoo
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path, cleanLocal.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, TaskOrderSpecs.any(generate.fullPath, cleanFooLocal.fullPath))
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
        }
    }

    def "multiple destroyer tasks listed on the command line followed by producers can run concurrently and are executed in the correct order"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo').shouldBlock()
        def cleanBar = bar.task('cleanBar').destroys('build/bar').dependsOn(cleanFoo)
        def cleanBarLocal = bar.task('cleanBarLocalState').destroys('build/bar-local').shouldBlock()
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').outputs('build/foo')
        def generateBar = bar.task('generateBar').outputs('build/bar').localState('build/bar-local')
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        server.start()

        writeAllFiles()

        expect:
        2.times {
            server.expectConcurrent(cleanFoo.path, cleanBarLocal.path)

            args '--parallel', '--max-workers=2'
            succeeds(clean.path, cleanBarLocal.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanBarLocal.fullPath, generateBar.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, generate.fullPath)
        }
    }

    def "a destroyer task finalized by a task in another project will run before producer tasks if ordered first"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanBar = bar.task('cleanBar').destroys('build/bar')
        def cleanFoo = foo.task('cleanFoo').destroys('build/foo').finalizedBy(cleanBar)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').outputs('build/foo')
        def generateBar = bar.task('generateBar').outputs('build/bar')
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
        }
    }

    def "a destroyer task finalizing both a destroyer and a producer task will run after producer tasks"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanBar = bar.task('cleanBar').destroys('build/bar')
        def cleanFoo = foo.task('cleanFoo').destroys('build/foo').finalizedBy(cleanBar)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').outputs('build/foo')
        def generateBar = bar.task('generateBar').outputs('build/bar').finalizedBy(cleanBar)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(generateBar.fullPath, TaskOrderSpecs.any(generate.fullPath, cleanBar.fullPath))
        }
    }

    def "a task that is neither a producer nor a destroyer can run concurrently with destroyers"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo').shouldBlock()
        def exec = bar.task('exec').shouldBlock()

        server.start()

        writeAllFiles()

        expect:
        2.times {
            server.expectConcurrent(cleanFoo.path, exec.path)

            args '--parallel', '--max-workers=2'
            succeeds(cleanFoo.path, exec.path)
        }
    }

    def "destroyers and producers in different projects can run concurrently when they have no dependencies"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo').shouldBlock()
        def cleanBar = bar.task('cleanBar').destroys('build/bar')
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').outputs('build/foo')
        def generateBar = bar.task('generateBar').outputs('build/bar').shouldBlock()
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        server.start()

        writeAllFiles()

        expect:
        2.times {
            server.expectConcurrent(cleanFoo.path, generateBar.path)

            args '--parallel', '--max-workers=2', '--rerun-tasks' // --rerun-tasks so that tasks are not up-to-date on second invocation
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
        }
    }

    def "destroyer task that mustRunAfter a task in another project will run before producer tasks when ordered first"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar').mustRunAfter(cleanFoo)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').outputs('build/foo')
        def generateBar = bar.task('generateBar').outputs('build/bar')
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/20195")
    def "destroyer task that is a finalizer of a producer task will run after the producer even when ordered first (type: #type)"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar')
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').produces('build/foo', type).finalizedBy(cleanFoo)
        def generateBar = bar.task('generateBar').produces('build/bar', type)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(generateFoo.fullPath, cleanFoo.fullPath, clean.fullPath)
            result.assertTaskOrder(generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
        }

        where:
        type << ProductionType.values()
    }

    @Issue("https://github.com/gradle/gradle/issues/20195")
    def "destroyer task that is a finalizer of a producer task and also a dependency will run after the producer even when ordered first (type: #type)"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar').dependsOn(cleanFoo)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').produces('build/foo', type).finalizedBy(cleanFoo)
        def generateBar = bar.task('generateBar').produces('build/bar', type)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)

            // cleanFoo must run after generateFoo, as cleanFoo finalizes generateFoo
            // cleanFoo must run after generate, as cleanFoo destroys an output produced by generateFoo and consumed by generate
            result.assertTaskOrder(generateFoo.fullPath, generate.fullPath, cleanFoo.fullPath, clean.fullPath)

            // cleanBar depends on cleanFoo, but cleanFoo must run after generate (per above)
            result.assertTaskOrder(generateBar.fullPath, generate.fullPath, cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
        }

        where:
        type << ProductionType.values()
    }

    @Issue("https://github.com/gradle/gradle/issues/20272")
    def "a destroyer task that mustRunAfter a task that does not get executed will run before producer tasks when ordered first (type: #type)"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanSpecialBar = bar.task('cleanSpecialBar')
        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar').dependsOn(cleanFoo).mustRunAfter(cleanSpecialBar)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').produces('build/foo', type)
        def generateBar = bar.task('generateBar').produces('build/bar', type)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
        }

        where:
        type << ProductionType.values()
    }

    def "build finishes with --continue even when producer task fails"() {
        def clean = rootBuild.task('clean').destroys('build')
        def compileJava = rootBuild.task('compileJava').outputs('build/classes/java').fail()
        def compileGroovy = rootBuild.task('compileGroovy').outputs('build/classes/groovy').dependsOn(compileJava)
        def classes = rootBuild.task('classes').dependsOn(compileJava).dependsOn(compileGroovy)

        writeAllFiles()

        when:
        fails(clean.path, classes.path, '--continue')
        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
        failure.assertHasFailures(1)
        outputDoesNotContain('Unable to make progress running work.')
    }
}
