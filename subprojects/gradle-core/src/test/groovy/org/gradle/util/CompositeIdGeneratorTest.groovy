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
import org.junit.Test
import org.junit.runner.RunWith
import static org.hamcrest.Matchers.*
import static org.gradle.util.Matchers.*
import static org.junit.Assert.*

@RunWith(JMock.class)
class CompositeIdGeneratorTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final IdGenerator<?> target = context.mock(IdGenerator.class)
    private final CompositeIdGenerator generator = new CompositeIdGenerator('scope', target)

    @Test
    public void createsACompositeId() {
        Object original = 12
        context.checking {
            one(target).generateId()
            will(returnValue(original))
        }
        Object id = generator.generateId()
        assertThat(id, not(sameInstance(original)))
        assertThat(id, not(equalTo(original)))
        assertThat(id.toString(), equalTo('scope.12'))
    }

    @Test
    public void compositeIdsAreNotEqualWhenOriginalIdsAreDifferent() {
        context.checking {
            one(target).generateId()
            will(returnValue(12))
            one(target).generateId()
            will(returnValue('original'))
        }

        assertThat(generator.generateId(), not(equalTo(generator.generateId())))
    }

    @Test
    public void compositeIdsAreNotEqualWhenScopesAreDifferent() {
        context.checking {
            exactly(2).of(target).generateId()
            will(returnValue(12))
        }

        CompositeIdGenerator other = new CompositeIdGenerator('other', target)
        assertThat(generator.generateId(), not(equalTo(other.generateId())))
    }

    @Test
    public void compositeIdsAreEqualWhenOriginalIdsAreEqual() {
        context.checking {
            exactly(2).of(target).generateId()
            will(returnValue(12))
        }

        assertThat(generator.generateId(), strictlyEqual(generator.generateId()))
    }
}


