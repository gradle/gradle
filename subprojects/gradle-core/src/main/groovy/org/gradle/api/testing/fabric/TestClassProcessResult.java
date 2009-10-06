package org.gradle.api.testing.fabric;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class TestClassProcessResult implements Serializable {

    private TestClassRunInfo testClassRunInfo;

    private Throwable executionErrorReason;
    private Throwable processorErrorReason;

    private List<TestMethodProcessResult> methodResults;

    public TestClassProcessResult(TestClassRunInfo testClassRunInfo) {
        this.testClassRunInfo = testClassRunInfo;
        methodResults = new ArrayList<TestMethodProcessResult>();
    }

    public void addMethodResult(TestMethodProcessResult methodResult) {
        methodResults.add(methodResult);
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(testClassRunInfo);
        out.writeObject(executionErrorReason);
        out.writeObject(processorErrorReason);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        testClassRunInfo = (TestClassRunInfo) in.readObject();
        executionErrorReason = (Throwable) in.readObject();
        processorErrorReason = (Throwable) in.readObject();
    }

    public TestClassRunInfo getTestClassRunInfo() {
        return testClassRunInfo;
    }

    public Throwable getExecutionErrorReason() {
        return executionErrorReason;
    }

    public Throwable getProcessorErrorReason() {
        return processorErrorReason;
    }

    void setExecutionErrorReason(Throwable executionErrorReason) {
        this.executionErrorReason = executionErrorReason;
    }

    void setProcessorErrorReason(Throwable processorErrorReason) {
        this.processorErrorReason = processorErrorReason;
    }
}
