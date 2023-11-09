/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.provider

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Task
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.api.problems.ProblemEmitter
import org.gradle.api.problems.internal.DefaultProblems
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskState
import org.gradle.internal.Describables
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.state.ModelObject
import org.gradle.problems.buildtree.ProblemDiagnosticsFactory
import org.gradle.util.TestUtil
import org.gradle.util.internal.RedirectStdOutAndErr
import org.junit.Rule
import spock.lang.Specification

class TransformBackedProviderTest extends Specification {
    @Rule
    RedirectStdOutAndErr outputs = new RedirectStdOutAndErr()
    def progressEventEmitter = Mock(BuildOperationProgressEventEmitter)
    def problemEmitter = Stub(ProblemEmitter)

    def setup() {
        DeprecationLogger.reset()
        DeprecationLogger.init(Stub(ProblemDiagnosticsFactory), WarningMode.All, progressEventEmitter, new DefaultProblems(problemEmitter))
    }

    def teardown() {
        DeprecationLogger.reset()
    }

    def "fails when calling isPresent() before producer task has completed"() {
        given:
        def property = propertyWithProducer()
        def provider = property.map { Integer.parseInt(it) }

        when:
        provider.isPresent()

        then:
        def ex = thrown(InvalidUserCodeException)
        ex.message == "Querying the mapped value of <prop> before <task> has completed is not supported"
        0 * progressEventEmitter._
    }

    def "fails when calling get() before producer task has completed"() {
        given:
        def property = propertyWithProducer()
        def provider = property.map { Integer.parseInt(it) }

        when:
        provider.get()

        then:
        def ex = thrown(InvalidUserCodeException)
        ex.message == "Querying the mapped value of <prop> before <task> has completed is not supported"
        0 * progressEventEmitter._
    }

    def "does not fail when calling get() after producer task has completed"() {
        given:
        def property = propertyWithCompletedProducer()
        def provider = property.map { Integer.parseInt(it) }

        when:
        provider.get()

        then:
        0 * progressEventEmitter._
    }

    def "fails when calling getOrNull() before producer task has completed"() {
        given:
        def property = propertyWithProducer()
        def provider = property.map { Integer.parseInt(it) }

        when:
        provider.getOrNull()

        then:
        def ex = thrown(InvalidUserCodeException)
        ex.message == "Querying the mapped value of <prop> before <task> has completed is not supported"
        0 * progressEventEmitter._
    }

    def "fails when calling getOrElse() before producer task has completed"() {
        given:
        def property = propertyWithProducer()
        def provider = property.map { Integer.parseInt(it) }

        when:
        provider.getOrElse(12)

        then:
        def ex = thrown(InvalidUserCodeException)
        ex.message == "Querying the mapped value of <prop> before <task> has completed is not supported"
        0 * progressEventEmitter._
    }

    def "fails when querying chained mapping before producer task has completed"() {
        given:
        def property = propertyWithProducer()
        def provider = property.map { Integer.parseInt(it) }.map { it + 2 }

        when:
        provider.get()

        then:
        def ex = thrown(InvalidUserCodeException)
        ex.message == "Querying the mapped value of map(<prop>) before <task> has completed is not supported"
        0 * progressEventEmitter._
    }

    def "fails when querying orElse() mapping before producer task has completed"() {
        given:
        def property = propertyWithProducer()
        def provider = property.map { Integer.parseInt(it) }.orElse(12)

        when:
        provider.get()

        then:
        def ex = thrown(InvalidUserCodeException)
        ex.message == "Querying the mapped value of <prop> before <task> has completed is not supported"
        0 * progressEventEmitter._
    }

    Property<String> propertyWithProducer() {
        def task = Mock(Task)
        def state = Mock(TaskState)
        _ * task.toString() >> "<task>"
        _ * task.state >> state
        def owner = Stub(ModelObject)
        _ * owner.taskThatOwnsThisObject >> task
        def property = TestUtil.objectFactory().property(String)
        property.attachOwner(owner, Describables.of("<prop>"))
        property.attachProducer(owner)
        property.set("12")
        return property
    }

    Property<String> propertyWithCompletedProducer() {
        def task = Mock(Task)
        def state = Mock(TaskState)
        _ * task.toString() >> "<task>"
        _ * task.state >> state
        _ * state.executed >> true
        def owner = Stub(ModelObject)
        _ * owner.taskThatOwnsThisObject >> task
        def property = TestUtil.objectFactory().property(String)
        property.attachOwner(owner, Describables.of("<prop>"))
        property.attachProducer(owner)
        property.set("12")
        return property
    }
}
