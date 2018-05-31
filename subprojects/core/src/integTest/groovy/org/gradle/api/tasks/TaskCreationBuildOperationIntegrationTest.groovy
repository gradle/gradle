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

import org.gradle.api.internal.tasks.RealizeTaskBuildOperationType
import org.gradle.api.internal.tasks.RegisterTaskBuildOperationType
import org.gradle.api.specs.Spec
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.build.BuildTestFixture
import org.gradle.internal.operations.BuildOperationType
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.test.fixtures.file.TestFile

class TaskCreationBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    def buildOperations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def "does not emit build ops when not collecting task stats"() {
        given:
        buildFile << """
            tasks.create("eager")
            tasks.replace("eager")
            tasks.createLater("deferred")
           
        """
        when:
        run 'deferred'

        then:
        verifyTaskIds()
        // no ops captured
        buildOperations.none(RegisterTaskBuildOperationType, withAnyPath(':eager', ':deferred'))
        buildOperations.none(RealizeTaskBuildOperationType, withAnyPath(':eager', ':deferred'))
    }

    def "emits registration build ops when tasks not realized"() {
        given:
        enable()
        stopBeforeTaskGraphCalculation()
        createLater('foo')

        when:
        runAndFail()

        then:
        verifyTaskIds()
        verifyTaskDetails(RegisterTaskBuildOperationType, withPath(':', ':foo'))
        buildOperations.none(RealizeTaskBuildOperationType)
    }

    def "emits registration, realization build ops when tasks later realized"() {
        given:
        enable()
        createLater('foo')
        createLater('bar')

        when:
        run 'foo'

        then:
        verifyTaskIds()
        verifyTaskDetails(RegisterTaskBuildOperationType, withPath(':', ':foo'))
        verifyTaskDetails(RegisterTaskBuildOperationType, withPath(':', ':bar'))
        verifyTaskDetails(RealizeTaskBuildOperationType, withPath(':', ':foo'), eager: false)
        buildOperations.none(RealizeTaskBuildOperationType, not(withPath(':', ':foo')))
    }

    def "emits creation, replace build ops when tasks eagerly created and replaced realized"() {
        given:
        enable()
        create('foo')
        create('bar')
        replace('bar')
        createLater('baz')
        replace('baz')

        when:
        run 'foo'

        then:
        verifyTaskIds()
        verifyTaskDetails(RealizeTaskBuildOperationType, withPath(':', ':foo'), eager: true)
        verifyTaskDetails(RealizeTaskBuildOperationType, { it.details.taskPath == ':bar' && !it.details.replacement }, eager: true)
        verifyTaskDetails(RealizeTaskBuildOperationType, { it.details.taskPath == ':bar' && it.details.replacement }, eager: true, replacement: true)
        def firstBazId = verifyTaskDetails(RegisterTaskBuildOperationType, withPath(':', ':baz'))
        verifyTaskDetails(RealizeTaskBuildOperationType, { it.details.taskPath == ':baz' && it.details.replacement }, eager: true, replacement: true)
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
            System.setProperty("org.gradle.internal.tasks.stats", "true")
        """
    }

    private void stopBeforeTaskGraphCalculation() {
        buildFile << """
            gradle.projectsEvaluated {
                throw new RuntimeException("stopping before task graph calculation")
            }
        """
    }

    private void createLater(String name) {
        buildFile << """
            tasks.createLater("$name")
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
        return { (it.details.buildPath as String) == buildPath &&  (it.details.taskPath as String) in (paths as List) }
    }

    private static Spec<? super BuildOperationRecord> withPath(String buildPath, String taskPath) {
        return { (it.details.buildPath as String) == buildPath && (it.details.taskPath as String) == taskPath }
    }

    private static Spec<? super BuildOperationRecord> not(Spec<? super BuildOperationRecord> predicate) {
        return { !predicate.isSatisfiedBy(it) }
    }

    private <T extends BuildOperationType<?, ?>> Long verifyTaskDetails(Map<String, ?> expectedDetails, Class<T> type, Spec<? super BuildOperationRecord> spec) {
        def op = buildOperations.only(type, spec)
        verifyTaskDetails(expectedDetails, op)
        op.details.taskId as Long
    }

    private <T extends BuildOperationType<?, ?>> Long verifyTaskDetails(Class<T> type, Spec<? super BuildOperationRecord> spec) {
        def op = buildOperations.only(type, spec)
        verifyTaskDetails([:], op)
        op.details.taskId as Long
    }

    static final Map<?, ?> DEFAULT_EXPECTED_DETAILS = [
        replacement: false,
        taskId: instanceOf(Number)
    ]

    private static <T extends BuildOperationType<?, ?>> void verifyTaskDetails(Map<String, ?> expectedDetails, BuildOperationRecord op) {
        (DEFAULT_EXPECTED_DETAILS + expectedDetails).each { key, value ->
            if (value instanceof Spec) {
                assert value.isSatisfiedBy(op.details[key])
            } else {
                assert op.details[key] == value
            }
        }
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
