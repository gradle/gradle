package org.gradle.api.testing.execution.control.refork;

import java.io.Serializable;

/**
 * @author Tom Eyckmans
 */
public interface DecisionContextItemConfig extends Serializable {

    /**
     *
     * Each implementation of this interface should have the following methods to control serialization: 
     *
     * private void writeObject(ObjectOutputStream out) throws IOException {
     *
     * }
     *
     * private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
     *
     * }
     *
     */
}
