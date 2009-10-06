package org.gradle.api.testing.execution.control.messages.server;

import org.gradle.api.testing.fabric.TestClassRunInfo;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * @author Tom Eyckmans
 */
public class ExecuteTestActionMessage extends AbstractTestServerControlMessage {

    private TestClassRunInfo testClassRunInfo;

    public ExecuteTestActionMessage(int pipelineId, TestClassRunInfo testClassRunInfo) {
        super(pipelineId);
        this.testClassRunInfo = testClassRunInfo;
    }

    public TestClassRunInfo getTestClassRunInfo() {
        return testClassRunInfo;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(testClassRunInfo);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        testClassRunInfo = (TestClassRunInfo) in.readObject();
    }
}
