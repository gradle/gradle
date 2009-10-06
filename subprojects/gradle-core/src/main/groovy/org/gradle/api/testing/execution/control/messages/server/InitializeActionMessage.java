package org.gradle.api.testing.execution.control.messages.server;

import org.gradle.api.testing.execution.control.refork.ReforkItemConfigs;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * @author Tom Eyckmans
 */
public class InitializeActionMessage extends AbstractTestServerControlMessage {

    private String testFrameworkId;
    private ReforkItemConfigs reforkItemConfigs;

    public InitializeActionMessage(int pipelineId) {
        super(pipelineId);
    }

    public String getTestFrameworkId() {
        return testFrameworkId;
    }

    public void setTestFrameworkId(String testFrameworkId) {
        this.testFrameworkId = testFrameworkId;
    }

    public ReforkItemConfigs getReforkItemConfigs() {
        return reforkItemConfigs;
    }

    public void setReforkItemConfigs(ReforkItemConfigs reforkItemConfigs) {
        this.reforkItemConfigs = reforkItemConfigs;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeUTF(testFrameworkId);
        out.writeObject(reforkItemConfigs);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        testFrameworkId = in.readUTF();
        reforkItemConfigs = (ReforkItemConfigs) in.readObject();
    }
}
