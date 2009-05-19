package org.gradle.api.testing;

import java.io.File;
import java.util.Set;

/**
 * @author Tom Eyckmans
 */
public interface TestFrameworkDetector {

    boolean processPossibleTestClass(File testClassFile);

    File getTestClassesDirectory();

    Set<String> getTestClassNames();
}
