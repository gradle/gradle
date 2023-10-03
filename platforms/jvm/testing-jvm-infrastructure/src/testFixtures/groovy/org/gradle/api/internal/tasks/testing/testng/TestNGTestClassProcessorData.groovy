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

import org.gradle.api.tasks.testing.TestFailure
import org.testng.ITestContext
import org.testng.ITestListener
import org.testng.ITestResult
import org.testng.annotations.AfterClass
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeClass
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Factory
import org.testng.annotations.Test

public class FailSkippedTestsListener implements ITestListener {
    void onTestStart(ITestResult result) {}
    void onTestSuccess(ITestResult result) {
        result.setStatus(ITestResult.FAILURE)
    }
    void onTestFailure(ITestResult result) {}
    void onTestSkipped(ITestResult result) {

    }
    void onTestFailedButWithinSuccessPercentage(ITestResult result) {}
    void onStart(ITestContext context) {}
    void onFinish(ITestContext context) {}
}

public class ATestNGClassWithSkippedTest {
    @org.testng.annotations.Test
    public void skipMe() {}
}

public class ATestNGClass {
    @BeforeClass public void beforeClass() {}
    @AfterClass public void afterClass() {}
    @BeforeMethod public void beforeMethod() {}
    @AfterMethod public void afterMethod() {}
    @Test public void ok() {}
    @Test(enabled = false) public void skipped() {}
}

public class ATestNGClassWithBeforeAndAfter {
    @BeforeClass public void beforeClass() { assert false }
    @AfterClass public void afterClass() { assert false }
    @BeforeMethod public void beforeMethod() { assert false }
    @AfterMethod public void afterMethod() { assert false }
    @Test public void ok() {}
}

public class ATestNGClassWithExpectedException {
    @Test(expectedExceptions = RuntimeException)
    public void ok() {
        throw new RuntimeException()
    }
}

public class ATestNGClassWithManyMethods {
    @Test public void ok() {}
    @Test public void ok2() {}
    @Test public void another() {}
    @Test public void yetAnother() {}
}

public class ATestNGClassWithGroups {
    @Test(groups="group1") public void group1() {}
    @Test(groups="group2") public void group2() {}
    @Test(groups="group2,group3") public void excluded() {}
    @Test(groups="group4") public void ignored() {}
}

public class ATestNGFactoryClass {
    @Factory
    public Object[] suite() {
        return [new ATestNGClass()] as Object[]
    }
}

public class ATestNGClassWithBrokenConstructor {
    static TestFailure failure = TestFailure.fromTestFrameworkFailure(new RuntimeException())
    def ATestNGClassWithBrokenConstructor() { throw failure.rawFailure }
    @Test public void test() {}
}

public class ATestNGClassWithBrokenSetupMethod {
    static TestFailure failure = TestFailure.fromTestFrameworkFailure(new RuntimeException())
    @BeforeMethod public void beforeMethod() { throw failure.rawFailure }
    @Test public void test() {}
}

public class ATestNGClassWithBrokenDependencyMethod {
    static TestFailure failure = TestFailure.fromTestFrameworkFailure(new RuntimeException())
    @Test public void beforeMethod() { throw failure.rawFailure }
    @Test(dependsOnMethods = 'beforeMethod') public void test() {}
}
