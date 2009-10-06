package org.gradle.api.testing.fabric;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * @author Tom Eyckmans
 */
public class TestMethodProcessResult implements Serializable {

    private String methodName;
    private Throwable thrownException;

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeUTF(methodName);
        out.writeObject(thrownException);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        methodName = in.readUTF();
        thrownException = (Throwable) in.readObject();
    }

}
