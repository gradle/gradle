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




package org.gradle.api.internal.tasks.testing

import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import org.gradle.api.tasks.testing.TestResult.ResultType

@RunWith(JMock.class)
class TestListenerAdapterTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final TestListener listener = context.mock(TestListener.class)
    private final TestListenerAdapter adapter = new TestListenerAdapter(listener)

    @Test
    public void forwardsTestToListener() {
        TestInternal test = context.mock(TestInternal.class)
        TestResult result = context.mock(TestResult.class)

        context.checking {
            allowing(test).isComposite()
            will(returnValue(false))
            one(listener).beforeTest(test)
            one(listener).afterTest(test, result)
        }

        adapter.started(test)
        adapter.completed(test, result)
    }

    @Test
    public void createsAnAggregatesResultForEmptyTestSuite() {
        TestInternal test = context.mock(TestInternal.class)

        context.checking {
            allowing(test).isComposite()
            will(returnValue(true))
            one(listener).beforeSuite(test)
            one(listener).afterSuite(withParam(sameInstance(test)), withParam(notNullValue()))
            will { arg, TestResult result ->
                assertThat(result.resultType, equalTo(ResultType.SUCCESS))
            }
        }

        adapter.started(test)
        adapter.completed(test, null)
    }
}
