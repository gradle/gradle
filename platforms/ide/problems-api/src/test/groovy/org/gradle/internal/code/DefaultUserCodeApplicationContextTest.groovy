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
import spock.lang.Specification

import java.util.function.Supplier

class DefaultUserCodeApplicationContextTest extends Specification {
    def context = new DefaultUserCodeApplicationContext()

    def "assigns id and associates with current thread"() {
        def source = Stub(UserCodeSource)
        def action = Mock(Action)

        expect:
        context.current() == null

        when:
        context.apply(source, action)

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
        context.apply(source, action)

        then:
        1 * action.execute(_) >> { UserCodeApplicationId id ->
            id1 = id
            context.apply(source2, action2)
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
        context.apply(source, action)

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
        context.apply(source, action)

        then:
        1 * action.execute(_) >> { UserCodeApplicationId id ->
            application1 = context.current()
            context.apply(source2, action2)
        }
        1 * action2.execute(_) >> { UserCodeApplicationId id ->
            assert id != application1.id
            assert context.current().id == id
            assert context.current().source == source2

            application1.reapply(runnable)

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
        context.apply(source, action)

        then:
        1 * action.execute(_) >> { UserCodeApplicationId id ->
            application1 = context.current()
            context.apply(source2, action2)
        }
        1 * action2.execute(_) >> { UserCodeApplicationId id ->
            def result = application1.reapply(supplier)
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
        context.apply(source, action)

        then:
        1 * action.execute(_) >> { UserCodeApplicationId id ->
            application1 = context.current()
        }

        when:
        def result = application1.reapply(supplier)

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
        def deferred = Mock(Action)
        def id1
        def decorated

        when:
        context.apply(source, action)

        then:
        1 * action.execute(_) >> { UserCodeApplicationId id ->
            id1 = id
            decorated = context.current().reapplyLater(deferred)
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
}
