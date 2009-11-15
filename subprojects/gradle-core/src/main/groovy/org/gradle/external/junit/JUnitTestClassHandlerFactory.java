/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.external.junit;

import java.lang.reflect.Method;

/**
 * @author Tom Eyckmans
 */
public class JUnitTestClassHandlerFactory {

    public JUnitTestClassHandler createTestClassHandler(final Class testClass)
    {
        JUnitTestClassHandler testClassHandler = null;

        try {
            final Method suiteMethod = testClass.getMethod("suite");

            testClassHandler = new JUnitTestSuiteTestClassHandler(suiteMethod);
        }
        catch (NoSuchMethodException noSuiteMethodException) {
            // test class is not a suite
            if (JUnit4Detecter.isJUnit4Available()) {
                testClassHandler = new JUnit4AdaptingTestClassHandler(testClass);
            }
            else {
                testClassHandler = new JUnitTestSuiteWrappingTestClassHandler(testClass);
            }
        }

        return testClassHandler;
    }

}
