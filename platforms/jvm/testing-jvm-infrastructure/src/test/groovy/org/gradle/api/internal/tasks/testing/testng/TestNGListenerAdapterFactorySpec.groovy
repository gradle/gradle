/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.testng

import org.testng.IConfigurationListener
import org.testng.ITestContext
import org.testng.ITestListener
import org.testng.ITestResult
import spock.lang.Specification

class TestNGListenerAdapterFactorySpec extends Specification {
    TestNGListenerAdapterFactory factory = new TestNGListenerAdapterFactory(getClass().classLoader)
    MyListener listener = Mock()
    MyListener2 otherListener = Mock()

    def "adapts to IConfigurationListener interface if available on class path"() {
        expect:
        factory.createAdapter(listener) instanceof IConfigurationListener
    }

    def "equals and hashcode methods works as expected"() {
        when:
        ITestListener listener1 = factory.createAdapter(listener);
        ITestListener listener2 = factory.createAdapter(listener);
        ITestListener differentListener = factory.createAdapter(otherListener);

        then:
        listener1.equals(listener2) && listener1.hashCode() == listener2.hashCode()
        !listener1.equals(listener)
        !listener1.equals(differentListener)
        !listener1.equals(null)
    }

    /**
     * covered by {@code org.gradle.testing.testng.TestNGIntegrationTest}
     */
    def "adapts to internal IConfigurationListener interface if available on class path"() {}

    def "adapter forwards all IConfigurationListener calls"() {
        IConfigurationListener adapter = factory.createAdapter(listener)
        ITestResult result = Mock()

        when:
        adapter.onConfigurationSuccess(result)
        adapter.onConfigurationFailure(result)
        adapter.onConfigurationSkip(result)

        then:
        1 * listener.onConfigurationSuccess(result)
        1 * listener.onConfigurationFailure(result)
        1 * listener.onConfigurationSkip(result)
    }

    def "adapter forwards all ITestListener calls"() {
        def adapter = factory.createAdapter(listener)
        def result = Mock(ITestResult)
        def test = Mock(ITestContext)

        when:
        adapter.onTestStart(result)
        adapter.onTestSuccess(result)
        adapter.onTestFailure(result)
        adapter.onTestSkipped(result)
        adapter.onTestFailedButWithinSuccessPercentage(result)
        adapter.onStart(test)
        adapter.onFinish(test)

        then:
        1 * listener.onTestStart(result)
        1 * listener.onTestSuccess(result)
        1 * listener.onTestFailure(result)
        1 * listener.onTestSkipped(result)
        1 * listener.onTestFailedButWithinSuccessPercentage(result)
        1 * listener.onStart(test)
        1 * listener.onFinish(test)
    }
}

interface MyListener extends ITestListener, IConfigurationListener {}
interface MyListener2 extends ITestListener, IConfigurationListener {}
