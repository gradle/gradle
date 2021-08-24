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
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Unroll


class ProducerTaskCommandLineOrderIntegrationTest extends AbstractCommandLineOrderTaskIntegrationTest {
    @Unroll
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

        when:
        args '--parallel', '--max-workers=2'
        succeeds(generate.path, clean.path)

        then:
        result.assertTaskOrder(generateFoo.fullPath, generateBar.fullPath, generate.fullPath)
        result.assertTaskOrder(generateFoo.fullPath, cleanFoo.fullPath, clean.fullPath)
        result.assertTaskOrder(generateBar.fullPath, cleanBar.fullPath, clean.fullPath)

        where:
        type << ProductionType.values()
    }

    @Unroll
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

        when:
        args '--parallel', '--max-workers=2'
        succeeds(generate.path, clean.path)

        then:
        result.assertTaskOrder(generateFoo.fullPath, generateBar.fullPath, generate.fullPath)
        result.assertTaskOrder(generateFoo.fullPath, cleanFoo.fullPath, clean.fullPath)
        result.assertTaskOrder(generateBar.fullPath, cleanBar.fullPath, clean.fullPath)

        where:
        type << ProductionType.values()
    }

    // Because we prevent destroyers from running in between producers and consumers, this does not currently work the way a user would
    // expect.  The intermediate destroyer task will be delayed until the consumers have all run.  If we were looking at the specific
    // outputs that a consumer required, rather than just the dependency relationships, we could support this case.
    @ToBeImplemented
    def "producer task followed by a destroyer task followed by a producer with a dependency on the first producer are run in the correct order"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFooLocal = foo.task('cleanFooLocalState').destroys('build/foo-local')
        def generateFoo = foo.task('generateFoo').outputs('build/foo').localState('build/foo-local')
        def generateBar = bar.task('generateBar').outputs('build/bar').dependsOn(generateFoo)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)
        def dist = rootBuild.task('dist').outputs('build/dist').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        when:
        args '--parallel', '--max-workers=2'
        succeeds(generate.path, cleanFooLocal.path, dist.path)

        then:
        // The user expects:
        // result.assertTaskOrder(generateFoo.fullPath, generateBar.fullPath, TaskOrderSpecs.any(generate.fullPath, cleanFooLocal.fullPath), dist.fullPath)
        result.assertTaskOrder(generateFoo.fullPath, generateBar.fullPath, TaskOrderSpecs.any(generate.fullPath, dist.fullPath), cleanFooLocal.fullPath)
    }

    def "producer task with a dependency in another project followed by a destroyer task followed by a producer are run in the correct order"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanBarLocal = bar.task('cleanBarLocalState').destroys('build/bar-local')
        def generateFoo = foo.task('generateFoo').outputs('build/foo').localState('build/foo-local')
        def generateBar = bar.task('generateBar').outputs('build/bar').dependsOn(generateFoo)
        def packageBarSources = bar.task('packageBarSources').outputs('build/pkg-src')

        writeAllFiles()

        when:
        args '--parallel', '--max-workers=2'
        succeeds(generateBar.path, cleanBarLocal.path, packageBarSources.path)

        then:
        result.assertTaskOrder(generateFoo.fullPath, generateBar.fullPath, cleanBarLocal.fullPath, packageBarSources.fullPath)
    }

    def "producer task with a dependency in another build followed by a destroyer task followed by a producer are run in the correct order"() {
        def foo = includedBuild('child').subproject(':foo')
        def bar = subproject(':bar')

        def cleanBarLocal = bar.task('cleanBarLocalState').destroys('build/bar-local')
        def generateFoo = foo.task('generateFoo').outputs('build/foo').localState('build/foo-local')
        def generateBar = bar.task('generateBar').outputs('build/bar').dependsOn(generateFoo)
        def packageBarSources = bar.task('packageBarSources').outputs('build/pkg-src')

        writeAllFiles()

        when:
        args '--parallel', '--max-workers=2'
        succeeds(generateBar.path, cleanBarLocal.path, packageBarSources.path)

        then:
        result.assertTaskOrder(generateFoo.fullPath, generateBar.fullPath, cleanBarLocal.fullPath, packageBarSources.fullPath)
    }

    def "multiple producer tasks listed on the command line followed by a destroyer can run concurrently and are executed in the correct order"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def generateFoo = foo.task('generateFoo').outputs('build/foo').shouldBlock()
        def generateBar = bar.task('generateBar').outputs('build/bar').dependsOn(generateFoo)
        def packageBarSources = bar.task('packageBarSources').outputs('build/pkg-src').shouldBlock()
        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar')
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)

        server.start()
        server.expectConcurrent(generateFoo.path, packageBarSources.path)

        writeAllFiles()

        when:
        args '--parallel', '--max-workers=2'
        succeeds(generateBar.path, packageBarSources.path, clean.path)

        then:
        result.assertTaskOrder(TaskOrderSpecs.any(generateFoo.fullPath, packageBarSources.fullPath), generateBar.fullPath)
        result.assertTaskOrder(generateFoo.fullPath, cleanFoo.fullPath, clean.fullPath)
        result.assertTaskOrder(packageBarSources.fullPath, generateBar.fullPath, cleanBar.fullPath, clean.fullPath)
    }
}
