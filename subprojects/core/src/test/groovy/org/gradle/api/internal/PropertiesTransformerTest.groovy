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
import org.gradle.util.internal.ClosureBackedAction
import spock.lang.Specification

class PropertiesTransformerTest extends Specification {
    final PropertiesTransformer transformer = new PropertiesTransformer()

    def 'returns original when no action specified'() {
        given:
        Map map = [test:'value']
        Properties original = props(map)
        Properties test = props(map)
        when:
        def result = transformer.transform(original)
        then:
        result == test
    }

    def 'action can access properties'() {
        given:
        Action<Properties> action = Mock()
        transformer.addAction(action)
        Properties original = props(removed:'value', changed:'old')
        when:
        def result = transformer.transform(original)
        then:
        action.execute(_) >> { Properties props ->
            props.remove('removed')
            props.changed = 'new'
            props.added = 'value'
        }
        props(changed:'new', added:'value') == result
    }

    def 'can use closure as action'() {
        given:
        transformer.addAction action { Properties props ->
            props.added = 'value'
        }
        when:
        def result = transformer.transform(new Properties())
        then:
        props(added:'value') == result
    }

    def 'can chain actions'() {
        given:
        transformer.addAction action { Properties props ->
            props.remove('removed')
        }
        transformer.addAction action { Properties props ->
            props.changed = 'new'
        }
        transformer.addAction action { Properties props ->
            props.added = 'value'
        }
        Properties original = props(removed:'value', changed:'old')
        when:
        def result = transformer.transform(original)
        then:
        props(changed:'new', added:'value') == result
    }

    def 'can transform to an OutputStream'() {
        given:
        transformer.addAction action { Properties props ->
            props.added = 'value'
        }
        ByteArrayOutputStream outstr = new ByteArrayOutputStream()

        when:
        transformer.transform(new Properties(), outstr)

        then:
        def result = new Properties()
        result.load(new ByteArrayInputStream(outstr.toByteArray()))
        result == [added: 'value']
    }

    Properties props(Map map) {
        Properties props = new Properties()
        props.putAll(map)
        return props
    }

    Action action(Closure c) {
        new ClosureBackedAction(c)
    }
}
