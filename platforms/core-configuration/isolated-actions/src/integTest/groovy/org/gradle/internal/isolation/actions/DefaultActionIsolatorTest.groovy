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

package org.gradle.internal.isolation.actions

import org.gradle.api.Action
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskContainer
import org.gradle.internal.cc.base.serialize.IsolateOwners
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.problems.NoOpProblemDiagnosticsFactory
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil
import org.junit.Rule

import java.util.concurrent.atomic.AtomicInteger

class DefaultActionIsolatorTest extends AbstractProjectBuilderSpec {

    CollectingTestOutputEventListener outputEventListener = new CollectingTestOutputEventListener()

    @Rule
    ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    ActionIsolator isolator

    def setup() {
        def diagnosticsFactory = new NoOpProblemDiagnosticsFactory()
        def buildOperationProgressEventEmitter = Mock(BuildOperationProgressEventEmitter)
        DeprecationLogger.init(WarningMode.All, buildOperationProgressEventEmitter, TestUtil.problemsService(), diagnosticsFactory.newUnlimitedStream())

        isolator = new DefaultActionIsolator(new IsolateOwners.OwnerGradle(project.gradle))
    }

    def cleanup() {
        DeprecationLogger.reset()
    }

    interface ManagedInteger {
        Property<Integer> getValue()
    }

    def "can isolate action without state"() {
        given:
        Action<ManagedInteger> input = (ManagedInteger x) -> {
            x.getValue().set(123)
        }

        when:
        ManagedInteger managed = executedIsolated(input)

        then:
        managed.getValue().get() == 123
        outputEventListener.events.findAll { it.logLevel == LogLevel.WARN }.isEmpty()
    }

    def "can isolate action capturing non-project state"() {
        AtomicInteger state = new AtomicInteger(123)
        Action<ManagedInteger> input = (ManagedInteger x) -> {
            x.getValue().set(state.get())
        }

        when:
        ManagedInteger managed = executedIsolated(input)

        then:
        managed.getValue().get() == 123
        outputEventListener.events.findAll { it.logLevel == LogLevel.WARN }.isEmpty()
    }

    def "cannot isolate actions capturing project instances"() {
        given:
        ProjectInternal theProject = project
        Action<ManagedInteger> input = x -> {
            assert theProject.owner.hasMutableState()
            x.getValue().set(123)
        }

        when:
        ManagedInteger managed = executeIsolatedWithoutLocks(input)

        then:
        managed.getValue().get() == 123
        assertHasDeprecations("Failed to isolate foo from project context: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache. This behavior has been deprecated. This will fail with an error in Gradle 10.0. foo actions must not reference project state.")
    }

    def "cannot isolate actions capturing internal project service"() {
        given:
        DomainObjectContext doc = project.services.get(DomainObjectContext)
        Action<ManagedInteger> input = x -> {
            assert doc.getModel().hasMutableState()
            x.getValue().set(123)
        }

        when:
        ManagedInteger managed = executeIsolatedWithoutLocks(input)

        then:
        managed.getValue().get() == 123
        assertHasDeprecations("Failed to isolate foo from project context: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache. This behavior has been deprecated. This will fail with an error in Gradle 10.0. foo actions must not reference project state.")
    }

    def "cannot isolate actions capturing public project service"() {
        given:
        TaskContainer tasks = project.getTasks()
        Action<ManagedInteger> input = x -> {
            println(tasks)
            x.getValue().set(123)
        }

        when:
        ManagedInteger managed = executeIsolatedWithoutLocks(input)

        then:
        managed.getValue().get() == 123
        assertHasDeprecations("Failed to isolate foo from project context: cannot serialize object of type 'org.gradle.api.internal.tasks.DefaultTaskContainer', a subtype of 'org.gradle.api.tasks.TaskContainer', as these are not supported with the configuration cache. This behavior has been deprecated. This will fail with an error in Gradle 10.0. foo actions must not reference project state.")
    }

    def "produces multiple problems in deprecation message when failing to serialize multiple fields"() {
        ConfigurationContainer confs = project.getConfigurations()
        TaskContainer tasks = project.getTasks()
        Action<ManagedInteger> input = x -> {
            println(tasks)
            println(confs)
            x.getValue().set(123)
        }

        when:
        ManagedInteger managed = executeIsolatedWithoutLocks(input)

        then:
        managed.getValue().get() == 123
        assertHasDeprecations("Failed to isolate foo from project context: [cannot serialize object of type 'org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer', a subtype of 'org.gradle.api.artifacts.ConfigurationContainer', as these are not supported with the configuration cache., cannot serialize object of type 'org.gradle.api.internal.tasks.DefaultTaskContainer', a subtype of 'org.gradle.api.tasks.TaskContainer', as these are not supported with the configuration cache.] This behavior has been deprecated. This will fail with an error in Gradle 10.0. foo actions must not reference project state.")
    }

    ManagedInteger executedIsolated(Action<ManagedInteger> input) {
        Action<ManagedInteger> result = isolator.isolateLenient(input, "foo", project.getOwner())

        ManagedInteger managed = project.objects.newInstance(ManagedInteger)
        result.execute(managed)

        return managed
    }

    ManagedInteger executeIsolatedWithoutLocks(Action<ManagedInteger> input) {
        Action<ManagedInteger> result = isolator.isolateLenient(input, "foo", project.getOwner())

        ManagedInteger managed = project.objects.newInstance(ManagedInteger)

        project.services.get(WorkerLeaseService).runAsIsolatedTask {
            assert !project.owner.hasMutableState()
            result.execute(managed)
        }

        return managed
    }

    void assertHasDeprecations(String... deprecations) {
        def events = outputEventListener.events.findAll { it.logLevel == LogLevel.WARN }
        assert events*.message == deprecations as List
    }

}
