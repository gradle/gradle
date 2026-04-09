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
import org.gradle.internal.time.MockClock
import org.gradle.util.Path
import spock.lang.Specification

import java.util.function.Supplier

class DefaultUserCodeApplicationContextTest extends Specification {
    def clock = MockClock.create()
    def applicationRegistry = Mock(UserCodeApplicationRegistry)
    def context = new DefaultUserCodeApplicationContext(
        clock,
        applicationRegistry
    )

    def "assigns id and associates with current thread"() {
        def source = Stub(UserCodeSource)
        def action = Mock(Action)

        expect:
        context.current() == null

        when:

        context.apply(source, null, action)

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
        context.apply(source, null, action)

        then:
        1 * action.execute(_) >> { UserCodeApplicationId id ->
            id1 = id
            context.apply(source2, null, action2)
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
        context.apply(source, null, action)

        then:
        1 * action.execute(_) >> { UserCodeApplicationId id ->
            id1 = id
            context.gradleRuntime(action2)
            assert context.current().id == id
            assert context.current().source == source
        }
        1 * action2.run() >> {
            // gradleRuntime clears the current application
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
        context.apply(source, null, action)

        then:
        1 * action.execute(_) >> { UserCodeApplicationId id ->
            application1 = context.current()
            context.apply(source2, null, action2)
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
        context.apply(source, null, action)

        then:
        1 * action.execute(_) >> { UserCodeApplicationId id ->
            application1 = context.current()
            context.apply(source2, null, action2)
        }
        1 * action2.execute(_) >> { UserCodeApplicationId id ->
            def result = application1.reapply(supplier, UserCodeApplicationContext.CodeType.GENERAL)
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
        context.apply(source, null, action)

        then:
        1 * action.execute(_) >> { UserCodeApplicationId id ->
            application1 = context.current()
        }

        when:
        def result = application1.reapply(supplier, UserCodeApplicationContext.CodeType.GENERAL)

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
        context.apply(source, null, action)

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
        context.apply(Stub(UserCodeSource), null) { id ->
            captured = context.current()
            clock.increment(10)
        }

        then:
        captured.getTotalDurationNs() == ms(10)
        captured.getDurationNsForType(UserCodeApplicationContext.CodeType.GENERAL) == ms(10)
        captured.getDurationNsForType(UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK) == 0
        captured.getDurationNsForType(UserCodeApplicationContext.CodeType.LISTENER) == 0
    }

    def "segregates time by code type"() {
        UserCodeApplicationContext.Application captured

        when:
        context.apply(Stub(UserCodeSource), null) { id ->
            captured = context.current()
            clock.increment(5)
        }
        // Reapply with different code types
        captured.reapply({ clock.increment(10) }, UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK)
        captured.reapply({ clock.increment(15) }, UserCodeApplicationContext.CodeType.LISTENER)

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
        context.apply(Stub(UserCodeSource), null) { id ->
            appA = context.current()
            clock.increment(10)  // A runs 0-10

            appB = null
            context.apply(Stub(UserCodeSource), null) { id2 ->
                appB = context.current()
                clock.increment(10)  // B runs 10-20

                appA.reapply({
                    clock.increment(10)  // Inner A runs 20-30
                }, UserCodeApplicationContext.CodeType.GENERAL)

                clock.increment(10)  // B runs 30-40
            }

            clock.increment(10)  // A runs 40-50
        }

        then:
        appA.getTotalDurationNs() == ms(30)
        appB.getTotalDurationNs() == ms(20)
    }

    def "reapplyAction tracks time under specified code type"() {
        UserCodeApplicationContext.Application captured
        def action = Mock(Action)

        when:
        context.apply(Stub(UserCodeSource), null) { id ->
            captured = context.current()
        }
        captured.reapplyAction(action, "arg", UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK)

        then:
        1 * action.execute("arg") >> {
            clock.increment(7)
        }
        captured.getDurationNsForType(UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK) == ms(7)
        captured.getDurationNsForType(UserCodeApplicationContext.CodeType.GENERAL) == 0
    }

    def "reapplySpec tracks time under specified code type"() {
        UserCodeApplicationContext.Application captured

        when:
        context.apply(Stub(UserCodeSource), null) { id ->
            captured = context.current()
        }
        def result = captured.reapplySpec({ clock.increment(3); return true }, "arg", UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK)

        then:
        result
        captured.getDurationNsForType(UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK) == ms(3)
    }

    def "time accumulates correctly when exception is thrown"() {
        UserCodeApplicationContext.Application captured

        when:
        context.apply(Stub(UserCodeSource), null) { id ->
            captured = context.current()
            clock.increment(5)
        }

        then:
        noExceptionThrown()

        when:
        captured.reapply({
            clock.increment(10)
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
        context.apply(Stub(UserCodeSource), null) { id ->
            captured = context.current()
            clock.increment(5)

            context.gradleRuntime {
                clock.increment(20)
            }

            clock.increment(5)
        }

        then:
        captured.getTotalDurationNs() == ms(10)
    }

    def "registers application with registry when project path is provided"() {
        def path = Path.path(":project")

        when:
        context.apply(Stub(UserCodeSource), path) { id -> }

        then:
        1 * applicationRegistry.register(path, _)
    }

    def "does not register application when project path is null"() {
        when:
        context.apply(Stub(UserCodeSource), null) { id -> }

        then:
        0 * applicationRegistry.register(_, _)
    }

    def "multiple reapply calls accumulate time"() {
        UserCodeApplicationContext.Application captured

        when:
        context.apply(Stub(UserCodeSource), null) { id ->
            captured = context.current()
        }
        captured.reapply({ clock.increment(3) }, UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK)
        captured.reapply({ clock.increment(7) }, UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK)
        captured.reapply({ clock.increment(5) }, UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK)

        then:
        captured.getDurationNsForType(UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK) == ms(15)
    }

    def "reapply with GENERAL code type tracks time"() {
        UserCodeApplicationContext.Application captured

        when:
        context.apply(Stub(UserCodeSource), null) { id ->
            captured = context.current()
        }
        captured.reapply({ clock.increment(10) }, UserCodeApplicationContext.CodeType.GENERAL)

        then:
        captured.getDurationNsForType(UserCodeApplicationContext.CodeType.GENERAL) == ms(10)
    }

    def "reapply supplier tracks time and returns value"() {
        UserCodeApplicationContext.Application captured

        when:
        context.apply(Stub(UserCodeSource), null) { id ->
            captured = context.current()
        }
        def result = captured.reapply({
            clock.increment(8)
            return "hello"
        } as java.util.function.Supplier, UserCodeApplicationContext.CodeType.LISTENER)

        then:
        result == "hello"
        captured.getDurationNsForType(UserCodeApplicationContext.CodeType.LISTENER) == ms(8)
    }

    def "reapplyActionLaterForCurrent wraps action with current application"() {
        def source = Stub(UserCodeSource)
        Action<?> deferred = Mock(Action)
        def id1
        Action<String> decorated

        when:
        context.apply(source, null) { id ->
            id1 = id
            decorated = context.reapplyActionLaterForCurrent(deferred, UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK)
        }

        then:
        0 * deferred._

        and:
        context.current() == null

        when:
        decorated.execute("arg")

        then:
        1 * deferred.execute("arg") >> {
            clock.increment(5)
        }
    }

    def "reapplyActionLaterForCurrent returns original action when no current application"() {
        def action = Mock(Action)

        expect:
        context.current() == null

        when:
        def result = context.reapplyActionLaterForCurrent(action, UserCodeApplicationContext.CodeType.GENERAL)

        then:
        result.is(action)
    }

    def "reapplySpecLaterForCurrent wraps spec with current application"() {
        def source = Stub(UserCodeSource)
        def spec = Mock(org.gradle.api.specs.Spec)
        org.gradle.api.specs.Spec<String> decorated

        when:
        context.apply(source, null) { id ->
            decorated = context.reapplySpecLaterForCurrent(spec, UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK)
        }

        then:
        0 * spec._

        when:
        def result = decorated.isSatisfiedBy("arg")

        then:
        1 * spec.isSatisfiedBy("arg") >> {
            clock.increment(5)
            return true
        }
        result
    }

    def "reapplySpecLaterForCurrent returns original spec when no current application"() {
        def spec = Mock(org.gradle.api.specs.Spec)

        expect:
        context.current() == null

        when:
        def result = context.reapplySpecLaterForCurrent(spec, UserCodeApplicationContext.CodeType.GENERAL)

        then:
        result.is(spec)
    }

    private static long ms(long millis) {
        return java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(millis)
    }
}
