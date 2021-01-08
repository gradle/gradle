/*
 * Copyright 2017 the original author or authors.
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

import org.testng.IMethodInstance;
import org.testng.ITestClass;

/**
 * Listener that reacts if a test class is started and finished.
 * This listener is invoked if the executing TestNG version is >= 6.9.10 which introduces the {@code org.testng.IClassListener}.
 *
 * @see TestNGListenerAdapterFactory
 */
public interface TestNGClassListener {

    void onBeforeClass(ITestClass testClass);

    /**
     * for compatibility reasons with testng 6.9.10
     */
    void onBeforeClass(ITestClass testClass, IMethodInstance mi);

    void onAfterClass(ITestClass testClass);

    /**
     * for compatibility reasons with testng 6.9.10
     */
    void onAfterClass(ITestClass testClass, IMethodInstance mi);
}
