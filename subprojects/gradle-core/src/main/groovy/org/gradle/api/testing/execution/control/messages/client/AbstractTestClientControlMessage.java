package org.gradle.api.testing.execution.control.messages.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * @author Tom Eyckmans
 */
public abstract class AbstractTestClientControlMessage implements TestClientControlMessage {
    private int forkId;

    protected AbstractTestClientControlMessage(final int forkId) {
        this.forkId = forkId;
    }

    public int getForkId() {
        return forkId;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeInt(forkId);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        forkId = in.readInt();
    }
}
