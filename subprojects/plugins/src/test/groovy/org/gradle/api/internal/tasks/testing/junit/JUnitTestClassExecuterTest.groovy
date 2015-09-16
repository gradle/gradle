/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit

import org.gradle.internal.concurrent.ThreadSafe
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runner.notification.RunListener
import org.junit.runners.Suite
import spock.lang.Specification
import spock.lang.Subject

class JUnitTestClassExecuterTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmp = new TestNameTestDirectoryProvider()

    def listener = Mock(RunListener)
    def spec = new JUnitSpec([] as Set, [] as Set, ["*AllFooTests"] as Set)

    @Subject classExecuter = withSpec(spec)

    JUnitTestClassExecuter withSpec(spec) {
        new JUnitTestClassExecuter(getClass().getClassLoader(), spec, listener, new NoopTestClassExecutionListener())
    }

    private static class NoopTestClassExecutionListener implements TestClassExecutionListener, ThreadSafe {
        @Override
        void testClassStarted(String testClassName) { }
        @Override
        void testClassFinished(Throwable failure) { }
    }

    def executesFullContentsOfAMatchingClassName() {
        def description = Description.createTestDescription(FooServerTest.class, "testFoo")

        when:
        classExecuter.execute(AllFooTests.class.getName())

        then:
        1 * listener.testStarted(description)
        1 * listener.testFinished(description)
    }

    @RunWith(Suite.class)
    @Suite.SuiteClasses([FooServerTest.class])
    public static class AllFooTests {
    }

    public static class FooServerTest {
        @Test
        public void testFoo() { }
    }
}
