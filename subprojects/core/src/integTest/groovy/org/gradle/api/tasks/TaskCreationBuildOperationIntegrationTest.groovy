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
import org.gradle.integtests.fixtures.ToBeFixedForIsolatedProjects
import org.gradle.integtests.fixtures.build.BuildTestFixture
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.operations.BuildOperationType
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.util.Path

import java.util.stream.Collectors

import static java.util.stream.Collectors.groupingBy
import static java.util.stream.Collectors.mapping

class TaskCreationBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    def buildOperations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def "configure actions for eager creation are nested in realization build op"() {
        buildFile << """
            tasks.all {
                logger.lifecycle 'all'
            }
            tasks.configureEach {
                logger.lifecycle 'configureEach'
            }
            tasks.create('foo') {
                logger.lifecycle 'create'
            }
            tasks.all {
                logger.lifecycle 'too late'
            }
        """

        when:
        run('foo')

        then:
        verifyTaskIds()
        def realize = verifyTaskDetails(RealizeTaskBuildOperationType, withPath(':', ':foo'))
        with(realize) {
            progress.size() == 1
            with(progress[0]) {
                detailsClassName == LogEvent.name
                details.message.startsWith("create")
            }

            children.size() == 2
            with(children[0]) {
                progress.size() == 1
                progress[0].detailsClassName == LogEvent.name
                progress[0].details.message.startsWith("all")
            }
            with(children[1]) {
                progress.size() == 1
                progress[0].detailsClassName == LogEvent.name
                progress[0].details.message.startsWith("configureEach")
            }

        }
    }

    def "configure actions for lazy creation are nested in realization build op"() {
        buildFile << """
            tasks.configureEach {
                logger.lifecycle 'configureEach'
            }
            def p = tasks.register('foo') {
                logger.lifecycle 'register'
            }
            p.configure {
                logger.lifecycle 'configure'
            }
        """

        when:
        run('foo')

        then:
        verifyTaskIds()
        def realize = verifyTaskDetails(RealizeTaskBuildOperationType, withPath(':', ':foo'))
        with(realize) {
            children.size() == 3
            with(children[0]) {
                progress.size() == 1
                progress[0].detailsClassName == LogEvent.name
                progress[0].details.message.startsWith("configureEach")
            }
            with(children[1]) {
                progress.size() == 1
                progress[0].detailsClassName == LogEvent.name
                progress[0].details.message.startsWith("register")
            }
            with(children[2]) {
                progress.size() == 1
                progress[0].detailsClassName == LogEvent.name
                progress[0].details.message.startsWith("configure")
            }
        }
    }

    def "emits registration build ops when tasks not realized"() {
        given:
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
        register('foo')
        register('bar')
        buildFile << """
            tasks.named("foo").configure {
                tasks.named("bar").get()
            }
        """

        when:
        run("foo")

        then:
        verifyTaskIds()
        verifyTaskDetails(RegisterTaskBuildOperationType, withPath(':', ':foo')).children.empty
        def realize = verifyTaskDetails(RealizeTaskBuildOperationType, withPath(':', ':foo'))
        realize.children.size() == 1
        def configure = realize.children[0]
        configure.children.size() == 1
        buildOperations.isType(configure.children[0], RealizeTaskBuildOperationType)
        withPath(":", ":bar").isSatisfiedBy(configure.children[0])
    }

    def "emits registration, realization build ops when tasks later realized"() {
        given:
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

    @ToBeFixedForIsolatedProjects(because = "allprojects, subprojects")
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
        includedBuild('buildSrc').multiProjectBuild('buildSrc', ['sub']) {
            buildFile << """
                allprojects { apply plugin: 'java-library' }
                dependencies { implementation(project(":sub")) }
            """
        }
        includedBuild('comp').multiProjectBuild('comp', ['sub'], createTasks).settingsFile
        buildFile << """
            tasks.named('build').configure { it.dependsOn gradle.includedBuild('comp').task(':build') }
        """

        when:
        run 'build'

        then:
        verifyTaskIds()
        def expectedTasks = [
            withPath(':buildSrc', ':sub:jar'),
            withPath(':buildSrc', ':jar'),
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
        verifyUniqueIdPerTaskPath()
    }

    private void verifyUniqueIdPerTaskPath() {
        def allRealizeOps = buildOperations.all(RealizeTaskBuildOperationType)
        def idsByTaskPath = allRealizeOps.stream()
                .map(it -> it.details)
                .collect(groupingBy(
                        { it -> Path.path((String) it.buildPath).append(Path.path((String) it.taskPath)) },
                        mapping({ it -> (Long) it.taskId }, Collectors.toSet())
                ))
        idsByTaskPath.each { entry ->
            assert entry.value.size() == 1
        }
    }

    private static Spec<? super BuildOperationRecord> withAnyPath(String buildPath, String... paths) {
        return { (it.details.buildPath as String) == buildPath && (it.details.taskPath as String) in (paths as List) }
    }

    private static Spec<? super BuildOperationRecord> withPath(String buildPath, String taskPath) {
        return { (it.details.buildPath as String) == buildPath && (it.details.taskPath as String) == taskPath }
    }

    private static Spec<? super BuildOperationRecord> not(Spec<? super BuildOperationRecord> predicate) {
        return { !predicate.isSatisfiedBy(it) }
    }

    private <T extends BuildOperationType<?, ?>> BuildOperationRecord verifyTaskDetails(Map<String, ?> expectedDetails, Class<T> type, Spec<? super BuildOperationRecord> spec) {
        def ops = buildOperations.all(type, spec)
        assert !ops.empty
        if (type == RealizeTaskBuildOperationType && GradleContextualExecuter.configCache) {
            // When using load after store, the task will be realized twice:
            //  - at configuration time
            //  - after loading from the configuration cache
            // Though even with load after store we can't assume == 2 here, since some of the tests fail the build before loading form the configuration cache.
            assert ops.size() <= 2
        } else {
            assert ops.size() == 1
        }
        // Only verify the first event since it is the one from the configuration phase.
        verifyTaskDetails(expectedDetails, ops.first())
        ops.first()
    }

    private <T extends BuildOperationType<?, ?>> BuildOperationRecord verifyTaskDetails(Class<T> type, Spec<? super BuildOperationRecord> spec) {
        verifyTaskDetails([:], type, spec)
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
