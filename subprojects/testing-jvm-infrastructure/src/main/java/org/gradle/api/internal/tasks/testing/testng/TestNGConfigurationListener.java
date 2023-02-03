/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.testng;

import org.testng.ITestResult;

/**
 * Our version of TestNG's IConfigurationListener. Can be adapted to
 * org.testng.internal.IConfigurationListener (from TestNG &lt; 6.2) or
 * org.testng.IConfigurationListener2 (from TestNG &gt;= 6.2). Became
 * necessary because TestNG 6.2 changed the package name of the former
 * interface from org.testng.internal to org.testng.
 *
 * @see TestNGListenerAdapterFactory
 */
public interface TestNGConfigurationListener {
    /**
     * Invoked whenever a configuration method succeeded.
     */
    void onConfigurationSuccess(ITestResult itr);

    /**
     * Invoked whenever a configuration method failed.
     */
    void onConfigurationFailure(ITestResult itr);

    /**
     * Invoked whenever a configuration method was skipped.
     */
    void onConfigurationSkip(ITestResult itr);
    /**
     * Invoked before a configuration method is invoked.
     *
     * Note: This method is only invoked for TestNG 6.2 or higher.
     */
    void beforeConfiguration(ITestResult tr);
}
