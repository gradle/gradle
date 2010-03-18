/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.util


import org.jmock.integration.junit4.JMock
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(JMock.class)
class DiffUtilTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    @Test
    public void notifiesListenerOfElementAddedToSet() {
        ChangeListener<String> listener = context.mock(ChangeListener.class)
        Set<String> a = ['a', 'b'] as Set
        Set<String> b = ['a', 'b', 'c'] as Set

        context.checking {
            one(listener).added('c')
        }

        DiffUtil.diff(b, a, listener);
    }

    @Test
    public void notifiesListenerOfElementRemovedFromSet() {
        ChangeListener<String> listener = context.mock(ChangeListener.class)
        Set<String> a = ['a', 'b'] as Set
        Set<String> b = ['a', 'b', 'c'] as Set

        context.checking {
            one(listener).removed('c')
        }

        DiffUtil.diff(a, b, listener);
    }

    @Test
    public void notifiesListenerOfElementAddedToMap() {
        ChangeListener<Map.Entry<String, String>> listener = context.mock(ChangeListener.class)
        Map<String, String> a = [a: 'value a', b: 'value b']
        Map<String, String> b = [a: 'value a', b: 'value b', c: 'value c']

        context.checking {
            one(listener).added(withParam(anything()))
            will { entry ->
                assertThat(entry.key, equalTo('c'))
                assertThat(entry.value, equalTo('value c'))
            }
        }

        DiffUtil.diff(b, a, listener);
    }

    @Test
    public void notifiesListenerOfElementRemovedFromMap() {
        ChangeListener<Map.Entry<String, String>> listener = context.mock(ChangeListener.class)
        Map<String, String> a = [a: 'value a', b: 'value b']
        Map<String, String> b = [a: 'value a', b: 'value b', c: 'value c']

        context.checking {
            one(listener).removed(withParam(anything()))
            will { entry ->
                assertThat(entry.key, equalTo('c'))
                assertThat(entry.value, equalTo('value c'))
            }
        }

        DiffUtil.diff(a, b, listener);
    }

    @Test
    public void notifiesListenerOfChangedElementInMap() {
        ChangeListener<Map.Entry<String, String>> listener = context.mock(ChangeListener.class)
        Map<String, String> a = [a: 'value a', b: 'value b']
        Map<String, String> b = [a: 'value a', b: 'new b']

        context.checking {
            one(listener).changed(withParam(anything()))
            will { entry ->
                assertThat(entry.key, equalTo('b'))
                assertThat(entry.value, equalTo('new b'))
            }
        }

        DiffUtil.diff(b, a, listener);
    }
}
