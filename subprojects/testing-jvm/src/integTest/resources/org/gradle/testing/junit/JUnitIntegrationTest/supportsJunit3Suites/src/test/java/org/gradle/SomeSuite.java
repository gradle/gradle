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

package org.gradle;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class SomeSuite extends TestCase {

    public static Test suite() {
        final TestSuite suite = new TestSuite();
        suite.addTestSuite(SomeTest1.class);
        suite.addTestSuite(SomeTest2.class);
        TestSetup wrappedSuite = new junit.extensions.TestSetup(suite) {

            protected void setUp() {
                System.out.println("stdout in TestSetup#setup");
                System.err.println("stderr in TestSetup#setup");
            }

            protected void tearDown() {
                System.out.println("stdout in TestSetup#teardown");
                System.err.println("stderr in TestSetup#teardown");
            }
        };

        return wrappedSuite;
    }
}
