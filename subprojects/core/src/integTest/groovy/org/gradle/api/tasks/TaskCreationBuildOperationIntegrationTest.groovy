/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.internal.tasks.DefaultTaskContainer
import org.gradle.api.internal.tasks.RealizeTaskBuildOperationType
import org.gradle.api.internal.tasks.RegisterTaskBuildOperationType
import org.gradle.api.specs.Spec
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.build.BuildTestFixture
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.operations.BuildOperationType
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.test.fixtures.file.TestFile

class TaskCreationBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    def buildOperations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def "does not emit build ops when not collecting task stats"() {
        given:
        create('eager')
        replace('eager')
        register('deferred')

        when:
        run 'deferred'

        then:
        // no ops captured
        buildOperations.none(RegisterTaskBuildOperationType, withAnyPath(':eager', ':deferred'))
        buildOperations.none(RealizeTaskBuildOperationType, withAnyPath(':eager', ':deferred'))
    }

    def "emits registration build ops when tasks not realized"() {
        given:
        enable()
        stopBeforeTaskGraphCalculation()
        register('foo')

        when:
        runAndFail()

        then:
        verifyTaskIds()
        verifyTaskDetails(RegisterTaskBuildOperationType, withPath(':', ':foo'))
        buildOperations.none(RealizeTaskBuildOperationType)
    }

    def "emits two ops for eager lazy realization"() {
        given:
        enable()
        stopBeforeTaskGraphCalculation()
        register('foo')

        when:
        args("-D${DefaultTaskContainer.EAGERLY_CREATE_LAZY_TASKS_PROPERTY}=true")
        runAndFail()

        then:
        verifyTaskIds()
        verifyTaskDetails(RegisterTaskBuildOperationType, withPath(':', ':foo')).children.empty
        def realize = verifyTaskDetails(RealizeTaskBuildOperationType, withPath(':', ':foo'))
        realize.children.empty
        realize.details.eager == false
    }

    def "op during realize are child ops"() {
        given:
        enable()
        register('foo')
        register('bar')
        buildFile << """
            tasks.configureEach {
                logger.lifecycle "output 1"
            }
            tasks.named("foo").configure {
                logger.lifecycle "output 2"
                tasks.named("bar").get()   
            }
        """

        when:
        run("foo")

        then:
        verifyTaskIds()
        verifyTaskDetails(RegisterTaskBuildOperationType, withPath(':', ':foo')).children.empty
        def realize = verifyTaskDetails(RealizeTaskBuildOperationType, withPath(':', ':foo'))
        realize.progress.size() == 2
        realize.progress[0].detailsClassName == LogEvent.name
        realize.progress[0].details.message.startsWith("output 1")
        realize.progress[1].detailsClassName == LogEvent.name
        realize.progress[1].details.message.startsWith("output 2")
        realize.children.size() == 1
        buildOperations.isType(realize.children[0], RealizeTaskBuildOperationType)
        withPath(":", ":bar").isSatisfiedBy(realize.children[0])
    }

    def "emits registration, realization build ops when tasks later realized"() {
        given:
        enable()
        register('foo')
        register('bar')

        when:
        run 'foo'

        then:
        verifyTaskIds()
        verifyTaskDetails(RegisterTaskBuildOperationType, withPath(':', ':foo')).children.empty
        verifyTaskDetails(RegisterTaskBuildOperationType, withPath(':', ':bar')).children.empty
        verifyTaskDetails(RealizeTaskBuildOperationType, withPath(':', ':foo'), eager: false).children.empty
        buildOperations.none(RealizeTaskBuildOperationType, not(withPath(':', ':foo')))
    }

    def "emits creation, replace build ops when tasks eagerly created and replaced realized"() {
        given:
        enable()
        create('foo')
        create('bar')
        replace('bar')
        register('baz')
        replace('baz')

        when:
        run 'foo'

        then:
        verifyTaskIds()
        verifyTaskDetails(RealizeTaskBuildOperationType, withPath(':', ':foo'), eager: true).children.empty
        verifyTaskDetails(RealizeTaskBuildOperationType, { it.details.taskPath == ':bar' && !it.details.replacement }, eager: true).children.empty
        verifyTaskDetails(RealizeTaskBuildOperationType, { it.details.taskPath == ':bar' && it.details.replacement }, eager: true, replacement: true).children.empty
        def bazOp = verifyTaskDetails(RegisterTaskBuildOperationType, withPath(':', ':baz'))
        bazOp.children.empty
        def firstBazId = bazOp.details.taskId
        verifyTaskDetails(RealizeTaskBuildOperationType, { it.details.taskPath == ':baz' && it.details.replacement }, eager: true, replacement: true).children.empty
        // original baz task never realized
        buildOperations.none(RealizeTaskBuildOperationType, { it.details.taskId == firstBazId })
    }

    def "registration and realization ops have correct paths"() {
        given:
        def createTasks = {
            buildFile << """
                apply plugin: 'base'
                subprojects {
                    apply plugin: 'base'
                    rootProject.tasks.named('build').configure {
                        it.dependsOn tasks.named('build')
                    }
                }
            """
        }
        multiProjectBuild('root', ['sub'], createTasks)
        def buildSrcDir = includedBuild('buildSrc').multiProjectBuild('buildSrc', ['sub'], createTasks)
        includedBuild('comp').multiProjectBuild('comp', ['sub'], createTasks).settingsFile
        buildFile << """
            tasks.named('build').configure { it.dependsOn gradle.includedBuild('comp').task(':build') }
        """
        enable(buildSrcDir.settingsFile) // set system prop early enough

        when:
        run 'build'

        then:
        verifyTaskIds()
        def expectedTasks = [
            withPath(':buildSrc', ':sub:build'),
            withPath(':buildSrc', ':build'),
            withPath(':comp', ':sub:build'),
            withPath(':comp', ':build'),
            withPath(':', ':sub:build'),
            withPath(':', ':build')
        ]
        expectedTasks.each {
            verifyTaskDetails(RegisterTaskBuildOperationType, it)
            verifyTaskDetails(RealizeTaskBuildOperationType, it, eager: false)
        }
    }

    private void enable(TestFile file = settingsFile) {
        file << """
            System.setProperty("org.gradle.internal.tasks.createops", "true")
        """
    }

    private void stopBeforeTaskGraphCalculation() {
        buildFile << """
            gradle.projectsEvaluated {
                throw new RuntimeException("stopping before task graph calculation")
            }
        """
    }

    private void register(String name) {
        buildFile << """
            tasks.register("$name")
        """
    }

    private void create(String name) {
        buildFile << """
            tasks.create("$name")
        """
    }

    private void replace(String name) {
        buildFile << """
            tasks.replace("$name")
        """
    }

    private void verifyTaskIds() {
        // all register ops have unique task id
        def allRegisterOps = buildOperations.all(RegisterTaskBuildOperationType)
        def allRegisterIds = allRegisterOps*.details*.taskId
        allRegisterIds == allRegisterIds as Set

        // all realize ops have unique task id
        def allRealizeOps = buildOperations.all(RealizeTaskBuildOperationType)
        def allRealizeIds = allRealizeOps*.details*.taskId
        allRealizeIds == allRealizeIds as Set

        // all non-eager realize ops should have corresponding register op
        allRealizeOps.findAll { !it.details.eager }.each {
            assert it.details.taskId in allRegisterIds
        }
    }

    private static Spec<? super BuildOperationRecord> withAnyPath(String buildPath, String... paths) {
        return { (it.details.buildPath as String) == buildPath && (it.details.taskPath as String) in (paths as List) }
    }

    private static Spec<? super BuildOperationRecord> withPath(String buildPath, String taskPath) {
        return { (it.details.buildPath as String) == buildPath && (it.details.taskPath as String) == taskPath }
    }

    private static Spec<? super BuildOperationRecord> noChildren(String buildPath, String taskPath) {
        return { (it.details.buildPath as String) == buildPath && (it.details.taskPath as String) == taskPath }
    }

    private static Spec<? super BuildOperationRecord> not(Spec<? super BuildOperationRecord> predicate) {
        return { !predicate.isSatisfiedBy(it) }
    }

    private <T extends BuildOperationType<?, ?>> BuildOperationRecord verifyTaskDetails(Map<String, ?> expectedDetails, Class<T> type, Spec<? super BuildOperationRecord> spec) {
        def op = buildOperations.only(type, spec)
        verifyTaskDetails(expectedDetails, op)
        op
    }

    private <T extends BuildOperationType<?, ?>> BuildOperationRecord verifyTaskDetails(Class<T> type, Spec<? super BuildOperationRecord> spec) {
        def op = buildOperations.only(type, spec)
        verifyTaskDetails([:], op)
        op
    }

    static final Map<?, ?> DEFAULT_EXPECTED_DETAILS = [
        replacement: false,
        taskId: instanceOf(Number)
    ]

    private static <T extends BuildOperationType<?, ?>> BuildOperationRecord verifyTaskDetails(Map<String, ?> expectedDetails, BuildOperationRecord op) {
        (DEFAULT_EXPECTED_DETAILS + expectedDetails).each { key, value ->
            if (value instanceof Spec) {
                assert value.isSatisfiedBy(op.details[key])
            } else {
                assert op.details[key] == value
            }
        }
        op
    }

    private static Spec<Object> instanceOf(Class<?> cls) {
        return { cls.isInstance(it) }
    }

    private BuildTestFixture includedBuild(String name) {
        if (name != 'buildSrc') {
            settingsFile << """
                includeBuild '$name'
            """
        }
        new BuildTestFixture(testDirectory.createDir(name))
    }
}
