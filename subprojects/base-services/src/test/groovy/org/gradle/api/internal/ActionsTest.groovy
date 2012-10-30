/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal

import org.gradle.api.Action
import org.gradle.api.Transformer
import spock.lang.Specification

import static org.gradle.api.internal.Actions.*

class ActionsTest extends Specification {

    def "do nothing indeed does nothing"() {
        given:
        def thing = Mock(Object)

        when:
        doNothing().execute(thing)

        then:
        0 * thing._(*_)
    }

    def "can do nothing on null"() {
        when:
        doNothing().execute(null)

        then:
        notThrown(Throwable)
    }

    def "transform before"() {
        given:
        def action = Mock(Action)

        when:
        transformBefore(action, new Transformer<Integer, String>() {
            Integer transform(String original) {
                Integer.valueOf(original, 10) * 2
            }
        }).execute("1")

        then:
        1 * action.execute(2)
    }

    def "cast before"() {
        given:
        def action = Mock(Action)

        when:
        castBefore(Integer, action).execute("1")

        then:
        def e = thrown(ClassCastException)

        when:
        castBefore(CharSequence, action).execute("1")

        then:
        1 * action.execute("1")
    }
}
