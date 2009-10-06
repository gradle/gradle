package org.gradle.api.testing.execution.control.messages.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * @author Tom Eyckmans
 */
public class ForkStartedMessage extends AbstractTestClientControlMessage {
    public ForkStartedMessage(final int forkId) {
        super(forkId);
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    }
}
