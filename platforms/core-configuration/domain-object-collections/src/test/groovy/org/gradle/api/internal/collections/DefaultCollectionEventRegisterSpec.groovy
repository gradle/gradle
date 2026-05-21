/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.collections

import org.gradle.api.Action
import org.gradle.api.internal.CollectionCallbackActionDecorator
import spock.lang.Specification

class DefaultCollectionEventRegisterSpec extends Specification {

    def r = register()
    def added = []
    def removed = []

    def executed = []

    protected CollectionEventRegister<CharSequence> register() {
        new DefaultCollectionEventRegister<>(CharSequence, { callback ->
            new Action<CharSequence>() {
                @Override
                void execute(CharSequence charSequence) {
                    executed << charSequence
                    callback.execute(charSequence)
                }
            }
        } as CollectionCallbackActionDecorator)
    }

    def "actions do nothing when none registered"() {
        expect:
        r.fireObjectAdded("a")
        r.fireObjectRemoved("a")
    }

    def "nothing subscribed when no actions registered"() {
        expect:
        !r.isSubscribed(null)
        !r.isSubscribed(CharSequence)
        !r.isSubscribed(String)
    }

    def "actions are invoked in order registered"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)

        given:
        r.registerEagerAddAction(String, action1)
        r.registerEagerAddAction(String, action2)

        when:
        r.fireObjectAdded("a")

        then:
        1 * action1.execute("a")

        then:
        1 * action2.execute("a")
        0 * _
    }

    def "types subscribed when actions registered"() {
        given:
        r.registerEagerAddAction(String, Stub(Action))

        expect:
        r.isSubscribed(null)
        r.isSubscribed(String)
        !r.isSubscribed(CharSequence)
        !r.isSubscribed(StringBuilder)

        r.registerEagerAddAction(CharSequence, Stub(Action))

        r.isSubscribed(null)
        r.isSubscribed(String)
        r.isSubscribed(CharSequence)
        r.isSubscribed(StringBuilder)

        r.registerEagerAddAction(StringBuilder, Stub(Action))

        r.isSubscribed(null)
        r.isSubscribed(String)
        r.isSubscribed(CharSequence)
        r.isSubscribed(StringBuilder)
    }

}
