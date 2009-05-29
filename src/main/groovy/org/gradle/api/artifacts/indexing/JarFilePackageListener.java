package org.gradle.api.artifacts.indexing;

/**
 * @author Tom Eyckmans
 */
public interface JarFilePackageListener {
    void receivePackage(String packageName);
}
