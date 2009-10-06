package org.gradle.api.testing.fabric;

import java.io.Serializable;

/**
 * @author Tom Eyckmans
 */
public interface TestClassRunInfo extends Serializable {
    String getTestClassName();
}
