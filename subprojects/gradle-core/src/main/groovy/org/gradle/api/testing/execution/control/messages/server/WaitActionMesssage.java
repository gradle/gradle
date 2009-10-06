package org.gradle.api.testing.execution.control.messages.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * @author Tom Eyckmans
 */
public class WaitActionMesssage extends AbstractTestServerControlMessage {

    private long timeToWait;

    public WaitActionMesssage(int pipelineId, long timeToWait) {
        super(pipelineId);
        this.timeToWait = timeToWait;
    }

    public long getTimeToWait() {
        return timeToWait;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeLong(timeToWait);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        timeToWait = in.readLong();
    }
}
