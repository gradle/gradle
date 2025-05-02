/*
 * Copyright 2013 the original author or authors.
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

import junit.extensions.TestSetup
import junit.framework.TestCase
import junit.framework.TestSuite
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runner.Runner
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunNotifier
import org.junit.runners.Parameterized
import org.junit.runners.Suite
import org.junit.runners.model.RunnerBuilder

import static org.junit.Assume.assumeTrue

public class ATestClass {
    @Test
    public void ok() {
    }
}

public class BTestClass {
    @Test
    public void ok() {
    }

    @Test
    public void coolName() {
    }
}

public class ATestClassWithIgnoredMethod {
    @Test
    @Ignore
    public void ignored() {
    }
}

public class ATestClassWithFailedTestAssumption {
    @Test
    public void assumed() {
        assumeTrue(false)
    }
}

@Ignore
public class AnIgnoredTestClass {
    @Test
    public void ignored() {
    }

    @Test
    public void ignored2() {
    }
}

public class ABrokenTestClass {
    @Test
    public void broken() {
        throw new RuntimeException("broken")
    }
}

public class ATestClassWithBrokenConstructor {
    def ATestClassWithBrokenConstructor() {
        throw new RuntimeException("broken")
    }

    @Test
    public void test() {
    }
}

public class ATestClassWithBrokenBeforeMethod {
    @Before
    public void setup() {
        throw new RuntimeException("broken")
    }

    @Test
    public void test() {
    }
}

public class ATestClassWithBrokenBeforeAndAfterMethod {
    @Before
    public void setup() {
        throw new RuntimeException("setup")
    }

    @After
    public void teardown() {
        throw new RuntimeException("teardown")
    }

    @Test
    public void test() {
    }
}

public class ATestClassWithBrokenBeforeClassMethod {
    @BeforeClass
    public static void setup() {
        throw new RuntimeException("broken")
    }

    @Test
    public void test() {
    }
}

public class AJunit3TestClass extends TestCase {
    public void testOk() {
    }
}

public class BJunit3TestClass extends TestCase {
    public void testOk() {
    }
}

public class AJunit3TestThatRenamesItself extends TestCase {
    public void testOk() {
        setName('another test')
    }
}

public class ABrokenJunit3TestClass extends TestCase {
    public void testBroken() {
        throw new RuntimeException("broken")
    }
}

public class ATestClassWithSuiteMethod {
    public static junit.framework.Test suite() {
        return new junit.framework.TestSuite(AJunit3TestClass.class, BJunit3TestClass.class)
    }
}

public class ATestClassWithBrokenSuiteMethod {
    public static junit.framework.Test suite() {
        throw new RuntimeException("broken")
    }
}

public class ATestSetUpWithBrokenSetUp extends TestSetup {
    def ATestSetUpWithBrokenSetUp() {
        super(new TestSuite(AJunit3TestClass.class))
    }

    protected void setUp() {
        throw new RuntimeException("broken")
    }

    public static junit.framework.Test suite() {
        return new ATestSetUpWithBrokenSetUp()
    }
}

@RunWith(CustomRunner.class)
public class ATestClassWithRunner {}

public class CustomRunner extends Runner {
    Class<?> type

    def CustomRunner(Class<?> type) {
        this.type = type
    }

    @Override
    public Description getDescription() {
        Description description = Description.createSuiteDescription(type)
        description.addChild(Description.createTestDescription(type, 'broken'))
        description.addChild(Description.createTestDescription(type, 'ok'))
        return description
    }

    @Override
    public void run(RunNotifier runNotifier) {
        // Run tests in 'parallel'
        Description test1 = Description.createTestDescription(type, 'broken')
        Description test2 = Description.createTestDescription(type, 'ok')
        runNotifier.fireTestStarted(test1)
        runNotifier.fireTestStarted(test2)
        runNotifier.fireTestFailure(new Failure(test1, new RuntimeException('broken custom runner')))
        runNotifier.fireTestFinished(test2)
        runNotifier.fireTestFinished(test1)
    }
}

@RunWith(CustomRunnerWithBrokenConstructor.class)
public class ATestClassWithUnconstructibleRunner {}

public class CustomRunnerWithBrokenConstructor extends Runner {
    def CustomRunnerWithBrokenConstructor(Class<?> type) {
        throw new RuntimeException("broken")
    }

    Description getDescription() {
        throw new UnsupportedOperationException()
    }

    void run(RunNotifier notifier) {
        throw new UnsupportedOperationException()
    }
}

@RunWith(CustomRunnerWithBrokenRunMethod.class)
public class ATestClassWithBrokenRunner {}

public class CustomRunnerWithBrokenRunMethod extends Runner {
    final Class<?> type

    def CustomRunnerWithBrokenRunMethod(Class<?> type) {
        this.type = type
    }

    Description getDescription() {
        return Description.createSuiteDescription(type)
    }

    void run(RunNotifier notifier) {
        throw new RuntimeException("broken")
    }
}

@RunWith(CustomRunnerWithRunMethodThatBreaksAfterRunningSomeTests.class)
public class ATestClassWithRunnerThatBreaksAfterRunningSomeTests {}

public class CustomRunnerWithRunMethodThatBreaksAfterRunningSomeTests extends Runner {
    final Class<?> type

    def CustomRunnerWithRunMethodThatBreaksAfterRunningSomeTests(Class<?> type) {
        this.type = type
    }

    Description getDescription() {
        return Description.createSuiteDescription(type)
    }

    void run(RunNotifier notifier) {
        notifier.fireTestStarted(Description.createTestDescription(type, "ok1"))
        notifier.fireTestFinished(Description.createTestDescription(type, "ok1"))
        notifier.fireTestStarted(Description.createTestDescription(type, "broken"))
        throw new RuntimeException("after tests")
    }
}

public class ATestClassWhichCannotBeLoaded {
    static {
        throw new NoClassDefFoundError()
    }

    @Test public void pass() {}
}

public class ATestClassWithSeveralMethods {
    @Test public void pass() {}
    @Test public void pass2() {}
    @Test public void passSlowly() {}
    @Test public void passSlowly2() {}
    @Test public void fail() { throw new RuntimeException("Boo!") }
}
public class ATestClassWithSlowMethods {
    @Test public void pass() {}
    @Test public void passSlowly() {}
}

@RunWith(Suite.class)
@Suite.SuiteClasses([ATestClass.class, BTestClass.class])
public class ATestSuite {
}

@RunWith(Suite.class)
@Suite.SuiteClasses([])
public class AnEmptyTestSuite {
}

public class CustomSuiteRunner extends Suite {
    public CustomSuiteRunner(Class<?> klass, RunnerBuilder builder) {
        super(builder, klass, ATestClass.class, BTestClass.class)
    }
}

@RunWith(CustomSuiteRunner.class)
public class ACustomSuite {
}

@RunWith(Parameterized.class)
public class AParameterizedTest {
    @Parameterized.Parameters
    public static Object[] data() {
        return [1, 3]
    }

    public AParameterizedTest(Integer parameter) {}

    @Test public void helpfulTest() {}
    @Test public void unhelpfulTest() {}
}

@RunWith(Parameterized.class)
public class AnEmptyParameterizedTest {
    @Parameterized.Parameters
    public static Object[] data() {
        return []
    }

    public AnEmptyParameterizedTest(Integer parameter) {}

    @Test public void helpfulTest() {}
    @Test public void unhelpfulTest() {}
}
