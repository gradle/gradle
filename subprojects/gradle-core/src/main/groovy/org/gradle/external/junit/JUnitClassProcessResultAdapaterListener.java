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
