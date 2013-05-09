package org.gradle.api.internal.tasks.testing.testng;

import org.gradle.api.GradleException;
import org.testng.ITestResult;

public class UnrepresentableParameterException extends GradleException {

    public UnrepresentableParameterException(ITestResult iTestResult, int paramIndex, Throwable cause) {
        super(
                String.format("Parameter %s of iteration %s of method '%s' toString() method threw exception",
                        paramIndex + 1, iTestResult.getMethod().getCurrentInvocationCount() + 1, iTestResult.getName()
                ),
                cause
        );
    }
}
