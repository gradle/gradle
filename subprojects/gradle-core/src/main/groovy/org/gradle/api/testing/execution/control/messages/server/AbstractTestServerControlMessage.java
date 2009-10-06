package org.gradle.api.testing.execution.control.messages.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * @author Tom Eyckmans
 */
public abstract class AbstractTestServerControlMessage implements TestServerControlMessage {
    private int pipelineId;

    protected AbstractTestServerControlMessage(int pipelineId) {
        this.pipelineId = pipelineId;
    }

    public int getPipelineId() {
        return pipelineId;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeInt(pipelineId);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        pipelineId = in.readInt();
    }
}
