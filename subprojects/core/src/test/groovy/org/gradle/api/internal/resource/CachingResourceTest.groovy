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


package org.gradle.api.internal.resource

import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

@RunWith(JMock.class)
class CachingResourceTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final Resource target = context.mock(Resource.class)
    private final CachingResource resource = new CachingResource(target)

    @Test
    public void fetchesAndCachesContentWhenExistenceIsChecked() {
        context.checking {
            one(target).getText()
            will(returnValue('content'))
        }

        assertTrue(resource.exists)
        assertThat(resource.text, equalTo('content'))
    }

    @Test
    public void fetchesAndCachesContentWhenContentIsRead() {
        context.checking {
            one(target).getText()
            will(returnValue('content'))
        }

        assertThat(resource.text, equalTo('content'))
        assertTrue(resource.exists)
    }
    
    @Test
    public void fetchesAndCachesContentForResourceThatDoesNotExist() {
        context.checking {
            one(target).getText()
            will(returnValue(null))
        }

        assertThat(resource.text, nullValue())
        assertFalse(resource.exists)
    }
}
