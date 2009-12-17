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
package org.gradle.api.tasks.testing;

import java.io.Serializable;

// todo: consider multithreading/multiprocess issues
// Teamcity has the concept of a "wave" of messages
// where each thread/process uses a unique wave id

/**
 * Interface for listening to test execution.  The intent is to be
 * framework agnostic.  Currently this interface can support feedback
 * from JUnit and TestNG tests.
 */
public interface TestListener {

    /**
     * Describes a suite of tests.  Many testing frameworks aggregate
     * tests into suites that get executed together.
     */
    public interface Suite extends Serializable {
        /**
         * @return The name of the suite.  Not guaranteed to be unique.
         */
        public String getName();
    }

    /**
     * Describes a single test.
     */
    public interface Test extends Serializable {
        /**
         * @return The name of the test.  Not guaranteed to be unique.
         */
        public String getName();
    }

    public enum ResultType { SUCCESS, FAILURE, SKIPPED }

    /**
     * Describes a test result.
     */
    public interface Result extends Serializable {
        /**
         * @return The type of result.  Generally one wants it to be SUCCESS!
         */
        public ResultType getResultType();

        /**
         * If the test failed with an exception, this will be the exception.  Some
         * test frameworks do not fail without an exception (JUnit), so in those cases
         * this method will never return null.  If the resultType is not FAILURE an IllegalStateException is thrown.
         * @return The exception, if any, logged for this test.  If none, a null is returned.
         * @throws IllegalStateException If the result type is anything other than FAILURE.
         */
        public Throwable getException(); // throws exception if type !=  FAILURE
    }

    /**
     * Called before a test suite is started.
     * @param suite The suite whose tests are about to be executed.
     */
    void suiteStarting(Suite suite);

    /**
     * Called after a test suite is finished.
     * @param suite The suite whose tests have finished being executed.
     */
    void suiteFinished(Suite suite);

    /**
     * Called before a test is started.
     * @param test The test which is about to be executed.
     */
    void testStarting(Test test);

    /**
     * Called after a test is finished.
     * @param test The test which has finished executing.
     */
    void testFinished(Test test, Result result);
}