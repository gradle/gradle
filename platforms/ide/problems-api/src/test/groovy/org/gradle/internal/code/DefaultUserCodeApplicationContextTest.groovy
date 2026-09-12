/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.code

import org.gradle.api.Action
import org.gradle.api.specs.Spec
import org.gradle.util.Path
import spock.lang.Specification

import java.util.concurrent.TimeUnit
import java.util.function.Supplier

class DefaultUserCodeApplicationContextTest extends Specification {

    MockNanoTimeProvider timeSource = new MockNanoTimeProvider()
    def target = UserCodeApplicationContext.Target.Other.INSTANCE
    def context = new DefaultUserCodeApplicationContext(timeSource)
    def recording = context.startRecording()

    def "assigns id and associates with current thread"() {
        def source = Stub(UserCodeSource)
        def action = Mock(Action)

        expect:
        context.current() == null

        when:
        context.apply(source, target, action)

        then:
        1 * action.execute(_) >> { UserCodeApplicationId id ->
            assert context.current().id == id
            assert context.current().source == source
        }

        and:
        context.current() == null
    }

    def "can nest application"() {
        def source = Stub(UserCodeSource)
        def source2 = Stub(UserCodeSource)
        def action = Mock(Action)
        def action2 = Mock(Action)
        def id1

        when:
        context.apply(source, target, action)

        then:
        1 * action.execute(_) >> { UserCodeApplicationId id ->
            id1 = id
            context.apply(source2, target, action2)
            assert context.current().id == id
            assert context.current().source == source
        }
        1 * action2.execute(_) >> { UserCodeApplicationId id ->
            assert id != id1
            assert context.current().id == id
            assert context.current().source == source2
        }

        and:
        context.current() == null
    }

    def "can nest Gradle code inside application"() {
        def source = Stub(UserCodeSource)
        def action = Mock(Action)
        def action2 = Mock(Runnable)
        def id1

        when:
        context.apply(source, target, action)

        then:
        1 * action.execute(_) >> { UserCodeApplicationId id ->
            id1 = id
            context.gradleRuntime(action2)
            assert context.current().id == id
            assert context.current().source == source
        }
        1 * action2.run() >> {
            assert context.current() == null
        }

        and:
        context.current() == null
    }

    def "can run actions registered by previous application"() {
        def source = Stub(UserCodeSource)
        def source2 = Stub(UserCodeSource)
        def action = Mock(Action)
        def runnable = Mock(Runnable)
        def action2 = Mock(Action)
        UserCodeApplicationContext.Application application1

        when:
        context.apply(source, target, action)

        then:
        1 * action.execute(_) >> { UserCodeApplicationId id ->
            application1 = context.current()
            context.apply(source2, target, action2)
        }
        1 * action2.execute(_) >> { UserCodeApplicationId id ->
            assert id != application1.id
            assert context.current().id == id
            assert context.current().source == source2

            application1.reapply(runnable, UserCodeApplicationContext.CodeType.GENERAL)

            assert context.current().id == id
            assert context.current().source == source2
        }
        1 * runnable.run() >> {
            assert context.current().id == application1.id
            assert context.current().source == source
        }

        and:
        context.current() == null
    }

    def "can run supplier registered by previous application"() {
        def source = Stub(UserCodeSource)
        def source2 = Stub(UserCodeSource)
        def action = Mock(Action)
        def supplier = Mock(Supplier)
        def action2 = Mock(Action)
        UserCodeApplicationContext.Application application1

        when:
        context.apply(source, target, action)

        then:
        1 * action.execute(_) >> { UserCodeApplicationId id ->
            application1 = context.current()
            context.apply(source2, target, action2)
        }
        1 * action2.execute(_) >> { UserCodeApplicationId id ->
            def result = application1.reapplySupplier(supplier, UserCodeApplicationContext.CodeType.GENERAL)
            assert result == "result"
        }
        1 * supplier.get() >> {
            assert context.current().id == application1.id
            assert context.current().source == source
            return "result"
        }

        and:
        context.current() == null
    }

    def "can retain application instance and later run actions against it"() {
        def source = Stub(UserCodeSource)
        def action = Mock(Action)
        def supplier = Mock(Supplier)
        UserCodeApplicationContext.Application application1

        when:
        context.apply(source, target, action)

        then:
        1 * action.execute(_) >> { UserCodeApplicationId id ->
            application1 = context.current()
        }

        when:
        def result = application1.reapplySupplier(supplier, UserCodeApplicationContext.CodeType.GENERAL)

        then:
        result == "result"

        and:
        supplier.get() >> {
            assert context.current() == application1
            return "result"
        }
    }

    def "can create actions for current application that can be run later"() {
        def source = Stub(UserCodeSource)
        def action = Mock(Action)
        Action<?> deferred = Mock(Action)
        def id1
        Action<String> decorated

        when:
        context.apply(source, target, action)

        then:
        1 * action.execute(_) >> { UserCodeApplicationId id ->
            id1 = id
            def current = context.current()
            decorated = { x ->
                current.reapplyAction(deferred, x, UserCodeApplicationContext.CodeType.GENERAL)
            }
        }
        0 * deferred._

        and:
        context.current() == null

        when:
        decorated.execute("arg")

        then:
        1 * deferred.execute("arg") >> {
            context.current().id == id1
            context.current().source == source
        }

        and:
        context.current() == null
    }

    def "accumulates time for a single application"() {
        UserCodeApplicationContext.Application captured

        when:
        context.apply(Stub(UserCodeSource), target) { id ->
            captured = context.current()
            timeSource.increment(10)
        }

        then:
        captured.getTotalDurationNs() == ms(10)
        captured.getDurationNsForType(UserCodeApplicationContext.CodeType.GENERAL) == ms(10)
        captured.getDurationNsForType(UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK) == 0
        captured.getDurationNsForType(UserCodeApplicationContext.CodeType.LISTENER) == 0
    }

    def "duration snapshots exclude the currently executing slice"() {
        long observedDuringExecution = -1

        when:
        context.apply(Stub(UserCodeSource), target) { id ->
            timeSource.increment(10)
            // Time is only committed when the application code completes
            observedDuringExecution = context.current().getTotalDurationNs()
        }

        then:
        observedDuringExecution == 0
    }

    def "segregates time by code type"() {
        UserCodeApplicationContext.Application captured

        when:
        context.apply(Stub(UserCodeSource), target) { id ->
            captured = context.current()
            timeSource.increment(5)
        }
        // Reapply with different code types
        captured.reapply({ timeSource.increment(10) }, UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK)
        captured.reapply({ timeSource.increment(15) }, UserCodeApplicationContext.CodeType.LISTENER)

        then:
        captured.getDurationNsForType(UserCodeApplicationContext.CodeType.GENERAL) == ms(5)
        captured.getDurationNsForType(UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK) == ms(10)
        captured.getDurationNsForType(UserCodeApplicationContext.CodeType.LISTENER) == ms(15)
        captured.getTotalDurationNs() == ms(30)
    }

    def "nested applications accumulate exclusive time"() {
        UserCodeApplicationContext.Application appA
        UserCodeApplicationContext.Application appB

        when:
        context.apply(Stub(UserCodeSource), target) { id ->
            appA = context.current()
            timeSource.increment(10)  // A runs 0-10

            appB = null
            context.apply(Stub(UserCodeSource), target) { id2 ->
                appB = context.current()
                timeSource.increment(10)  // B runs 10-20

                appA.reapply({
                    timeSource.increment(10)  // Inner A runs 20-30
                }, UserCodeApplicationContext.CodeType.GENERAL)

                timeSource.increment(10)  // B runs 30-40
            }

            timeSource.increment(10)  // A runs 40-50
        }

        then:
        appA.getTotalDurationNs() == ms(30)
        appB.getTotalDurationNs() == ms(20)
    }

    def "time accumulates correctly when exception is thrown"() {
        UserCodeApplicationContext.Application captured

        when:
        context.apply(Stub(UserCodeSource), target) { id ->
            captured = context.current()
            timeSource.increment(5)
        }

        then:
        noExceptionThrown()

        when:
        captured.reapply({
            timeSource.increment(10)
            throw new RuntimeException("boom")
        }, UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK)

        then:
        thrown(RuntimeException)
        captured.getDurationNsForType(UserCodeApplicationContext.CodeType.GENERAL) == ms(5)
        captured.getDurationNsForType(UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK) == ms(10)
    }

    def "gradleRuntime does not accumulate time on user applications"() {
        UserCodeApplicationContext.Application captured

        when:
        context.apply(Stub(UserCodeSource), target) { id ->
            captured = context.current()
            timeSource.increment(5)

            context.gradleRuntime {
                timeSource.increment(20)
            }

            timeSource.increment(5)
        }

        then:
        captured.getTotalDurationNs() == ms(10)
    }

    def "groups applications by the target they are applied to"() {
        def projectA = new UserCodeApplicationContext.Target.Project(Path.path(":a"))
        def projectB = new UserCodeApplicationContext.Target.Project(Path.path(":b"))

        when:
        context.apply(Stub(UserCodeSource), projectA) { id -> }
        context.apply(Stub(UserCodeSource), projectA) { id -> }
        context.apply(Stub(UserCodeSource), projectB) { id -> }
        context.apply(Stub(UserCodeSource), target) { id -> }

        then:
        // Targets are compared by value. Production logic registers and queries with distinct target instances
        context.getApplicationsFor(new UserCodeApplicationContext.Target.Project(Path.path(":a"))).size() == 2
        context.getApplicationsFor(new UserCodeApplicationContext.Target.Project(Path.path(":b"))).size() == 1
        context.getApplicationsFor(new UserCodeApplicationContext.Target.Project(Path.path(":unknown"))).empty

        when:
        def applications = recording.stop()

        then:
        applications.keySet() == [projectA, projectB, target] as Set
        applications.get(projectA).size() == 2
        applications.get(projectB).size() == 1
        applications.get(target).size() == 1
    }

    def "assigns unique ids increasing in application order"() {
        def ids = []

        when:
        context.apply(Stub(UserCodeSource), target) { id ->
            ids << id
            context.apply(Stub(UserCodeSource), target) { nestedId ->
                ids << nestedId
            }
        }
        context.apply(Stub(UserCodeSource), target) { id ->
            ids << id
        }

        then:
        // Consumers order plugin application results by id
        ids*.longValue() == [1L, 2L, 3L]
    }

    def "cannot apply or query applications when no recording is in progress"() {
        given:
        recording.stop()

        when:
        context.apply(Stub(UserCodeSource), target, {})

        then:
        thrown(IllegalStateException)

        when:
        context.getApplicationsFor(target)

        then:
        thrown(IllegalStateException)
    }

    def "cannot start a recording while one is in progress"() {
        when:
        context.startRecording()

        then:
        thrown(IllegalStateException)
    }

    def "cannot stop a recording that is not in progress"() {
        given:
        recording.stop()
        def newRecording = context.startRecording()

        when:
        recording.stop()

        then:
        thrown(IllegalStateException)

        when:
        def applications = newRecording.stop()

        then:
        applications.isEmpty()
    }

    def "multiple reapply calls accumulate time"() {
        UserCodeApplicationContext.Application captured

        when:
        context.apply(Stub(UserCodeSource), target) { id ->
            captured = context.current()
        }
        captured.reapply({ timeSource.increment(3) }, UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK)
        captured.reapply({ timeSource.increment(7) }, UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK)
        captured.reapply({ timeSource.increment(5) }, UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK)

        then:
        captured.getDurationNsForType(UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK) == ms(15)
    }

    def "reapply supplier tracks time and returns value"() {
        UserCodeApplicationContext.Application captured

        when:
        context.apply(Stub(UserCodeSource), target) { id ->
            captured = context.current()
        }
        def result = captured.reapplySupplier({
            timeSource.increment(8)
            return "hello"
        } as Supplier, UserCodeApplicationContext.CodeType.LISTENER)

        then:
        result == "hello"
        captured.getDurationNsForType(UserCodeApplicationContext.CodeType.LISTENER) == ms(8)
    }

    def "reapplySpec restores application and tracks time"() {
        def source = Stub(UserCodeSource)
        def spec = Mock(Spec)
        UserCodeApplicationContext.Application captured

        when:
        context.apply(source, target) { id ->
            captured = context.current()
        }
        def result = captured.reapplySpec(spec, "arg", UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK)

        then:
        1 * spec.isSatisfiedBy("arg") >> {
            assert context.current() == captured
            timeSource.increment(5)
            return true
        }
        result

        and:
        context.current() == null
        captured.getDurationNsForType(UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK) == ms(5)
    }

    private static long ms(long millis) {
        return TimeUnit.MILLISECONDS.toNanos(millis)
    }

    private class MockNanoTimeProvider implements DefaultUserCodeApplicationContext.NanoTimeProvider {
        long nanoTime = 0

        @Override
        long nanoTime() {
            return nanoTime
        }

        void increment(long millis) {
            nanoTime += TimeUnit.MILLISECONDS.toNanos(millis)
        }
    }

}
