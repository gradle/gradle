package org.gradle.api.testing.execution.control.messages.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * @author Tom Eyckmans
 */
public class StopForkActionMessage extends AbstractTestServerControlMessage {
    public StopForkActionMessage(int pipelineId) {
        super(pipelineId);
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    }
}
