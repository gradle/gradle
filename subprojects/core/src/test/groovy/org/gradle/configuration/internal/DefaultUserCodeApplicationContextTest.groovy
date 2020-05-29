/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configuration.internal

import org.gradle.api.Action
import org.gradle.internal.Describables
import spock.lang.Specification

class DefaultUserCodeApplicationContextTest extends Specification {
    def context = new DefaultUserCodeApplicationContext()

    def "assigns id and associates with current thread"() {
        def displayName = Describables.of("thing")
        def action = Mock(Action)

        expect:
        context.current() == null

        when:
        context.apply(displayName, action)

        then:
        1 * action.execute(_) >> { UserCodeApplicationId id ->
            assert context.current().id == id
            assert context.current().displayName == displayName
        }

        and:
        context.current() == null
    }

    def "can nest application"() {
        def displayName = Describables.of("thing 1")
        def displayName2 = Describables.of("thing 2")
        def action = Mock(Action)
        def action2 = Mock(Action)
        def id1

        when:
        context.apply(displayName, action)

        then:
        1 * action.execute(_) >> { UserCodeApplicationId id ->
            id1 = id
            context.apply(displayName2, action2)
            assert context.current().id == id
            assert context.current().displayName == displayName
        }
        1 * action2.execute(_) >> { UserCodeApplicationId id ->
            assert id != id1
            assert context.current().id == id
            assert context.current().displayName == displayName2
        }

        and:
        context.current() == null
    }

    def "can run actions registered by previous application"() {
        def displayName = Describables.of("thing 1")
        def displayName2 = Describables.of("thing 2")
        def action = Mock(Action)
        def runnable = Mock(Runnable)
        def action2 = Mock(Action)
        def application1

        when:
        context.apply(displayName, action)

        then:
        1 * action.execute(_) >> { UserCodeApplicationId id ->
            application1 = context.current()
            context.apply(displayName2, action2)
        }
        1 * action2.execute(_) >> { UserCodeApplicationId id ->
            assert id != application1.id
            assert context.current().id == id
            assert context.current().displayName == displayName2

            application1.reapply(runnable)

            assert context.current().id == id
            assert context.current().displayName == displayName2
        }
        1 * runnable.run() >> {
            assert context.current().id == application1.id
            assert context.current().displayName == displayName
        }

        and:
        context.current() == null
    }

    def "can create actions for current application that can be run later"() {
        def displayName = Describables.of("thing 1")
        def action = Mock(Action)
        def deferred = Mock(Action)
        def id1
        def decorated

        when:
        context.apply(displayName, action)

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
            context.current().displayName == displayName
        }

        and:
        context.current() == null
    }
}
