package org.gradle.api.testing.fabric;

import java.io.File;

/**
 * @author Tom Eyckmans
 */
public interface TestFrameworkDetector {

    boolean processPossibleTestClass(File testClassFile);

    File getTestClassesDirectory();
}
