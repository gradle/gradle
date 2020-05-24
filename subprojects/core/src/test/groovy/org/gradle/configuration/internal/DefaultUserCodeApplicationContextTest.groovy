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
        context.currentDisplayName() == null

        when:
        context.apply(displayName, action)

        then:
        1 * action.execute(_) >> { UserCodeApplicationId id ->
            assert context.current() == id
            assert context.currentDisplayName() == displayName
        }

        and:
        context.current() == null
        context.currentDisplayName() == null
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
        }
        1 * action2.execute(_) >> { UserCodeApplicationId id ->
            assert id != id1
            assert context.current() == id
            assert context.currentDisplayName() == displayName2
        }

        and:
        context.current() == null
        context.currentDisplayName() == null
    }

    def "can run actions registered by previous application"() {
        def displayName = Describables.of("thing 1")
        def displayName2 = Describables.of("thing 2")
        def action = Mock(Action)
        def runnable = Mock(Runnable)
        def action2 = Mock(Action)
        def id1

        when:
        context.apply(displayName, action)

        then:
        1 * action.execute(_) >> { UserCodeApplicationId id ->
            id1 = id
            context.apply(displayName2, action2)
        }
        1 * action2.execute(_) >> { UserCodeApplicationId id ->
            assert context.current() != id1
            assert context.currentDisplayName() == displayName2

            context.reapply(id1, runnable)

            assert context.current() == id
            assert context.currentDisplayName() == displayName2
        }
        1 * runnable.run() >> {
            assert context.current() == id1
            assert context.currentDisplayName() == null // currently not tracked
        }

        and:
        context.current() == null
        context.currentDisplayName() == null
    }
}
