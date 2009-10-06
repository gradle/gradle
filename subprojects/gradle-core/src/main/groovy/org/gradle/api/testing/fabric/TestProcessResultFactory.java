package org.gradle.api.testing.fabric;

/**
 * @author Tom Eyckmans
 */
public class TestProcessResultFactory {

    public TestClassProcessResult createEmptyClassResult(TestClassRunInfo testClasRunInfo) {
        return new TestClassProcessResult(testClasRunInfo);
    }

    public TestClassProcessResult createClassExecutionErrorResult(TestClassRunInfo testClassRunInfo, Throwable executionErrorReason) {
        final TestClassProcessResult classProcessResult = createEmptyClassResult(testClassRunInfo);

        classProcessResult.setExecutionErrorReason(executionErrorReason);

        return classProcessResult;
    }

    public TestClassProcessResult createClassProcessErrorResult(TestClassRunInfo testClassRunInfo, Throwable processErrorReason) {
        final TestClassProcessResult classProcessResult = createEmptyClassResult(testClassRunInfo);

        classProcessResult.setProcessorErrorReason(processErrorReason);

        return classProcessResult;
    }

}
