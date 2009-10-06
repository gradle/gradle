package org.gradle.api.testing.execution.control.messages.client;

import org.gradle.api.testing.execution.control.refork.ReforkDecisionContext;
import org.gradle.api.testing.fabric.TestClassProcessResult;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * @author Tom Eyckmans
 */
public class NextActionRequestMessage extends AbstractTestClientControlMessage {

    private TestClassProcessResult previousProcessedTestResult = null;
    private ReforkDecisionContext reforkDecisionContext = null;

    public NextActionRequestMessage(final int forkId) {
        super(forkId);
    }

    public TestClassProcessResult getPreviousProcessedTestResult() {
        return previousProcessedTestResult;
    }

    public ReforkDecisionContext getReforkDecisionContext() {
        return reforkDecisionContext;
    }

    public void setPreviousProcessedTestResult(TestClassProcessResult previousProcessedTestResult) {
        this.previousProcessedTestResult = previousProcessedTestResult;
    }

    public void setReforkDecisionContext(ReforkDecisionContext reforkDecisionContext) {
        this.reforkDecisionContext = reforkDecisionContext;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(previousProcessedTestResult);
        out.writeObject(reforkDecisionContext);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        previousProcessedTestResult = (TestClassProcessResult) in.readObject();
        reforkDecisionContext = (ReforkDecisionContext) in.readObject();
    }
}
