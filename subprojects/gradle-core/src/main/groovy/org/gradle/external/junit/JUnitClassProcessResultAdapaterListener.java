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

import junit.framework.TestListener;
import junit.framework.Test;
import junit.framework.AssertionFailedError;
import org.gradle.api.testing.fabric.TestClassProcessResult;
import org.gradle.api.testing.fabric.TestMethodProcessResult;
import org.gradle.api.testing.fabric.TestMethodProcessResultState;
import static org.gradle.external.junit.JUnitTestMethodProcessResultStates.*;

/**
 * @author Tom Eyckmans
 */
public class JUnitClassProcessResultAdapaterListener implements TestListener {
    private final TestClassProcessResult classProcessResult;

    private Test currentTest;
    private TestMethodProcessResultState currentTestState;
    private Throwable currentTestException;

    public JUnitClassProcessResultAdapaterListener(TestClassProcessResult classProcessResult) {
        if ( classProcessResult == null ) throw new IllegalArgumentException("classProcessResult == null!");

        this.classProcessResult = classProcessResult;
    }

    public void addError(Test test, Throwable throwable) {
        if ( currentTest != test )
            throw new IllegalStateException("currentTest("+currentTest+") != test("+test+")!");

        currentTestState = ERROR;
        currentTestException = throwable;
    }

    public void addFailure(Test test, AssertionFailedError assertionFailedError) {
        if ( currentTest != test )
            throw new IllegalStateException("currentTest("+currentTest+") != test("+test+")!");

        currentTestState = FAILURE;
        currentTestException = assertionFailedError;
    }

    public void endTest(Test test) {
        if ( currentTest != test )
            throw new IllegalStateException("currentTest("+currentTest+") != test("+test+")!");

        classProcessResult.addMethodResult(new TestMethodProcessResult(
                test.toString(),
                currentTestState,
                currentTestException
        ));
        currentTest = null;
        currentTestException = null;
    }

    public void startTest(Test test) {
        currentTest = test;
        currentTestState = SUCCESS;
        currentTestException = null;
    }
}
