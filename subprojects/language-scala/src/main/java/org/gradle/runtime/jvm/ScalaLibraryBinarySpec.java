package org.gradle.runtime.jvm;

import org.gradle.api.Incubating;
import org.gradle.runtime.base.BinarySpec;
import org.gradle.runtime.jvm.toolchain.ScalaToolChain;

import java.io.File;

/**
 * Definition of a JVM binary build for a {@link org.gradle.runtime.jvm.JvmLibrary}.
 */
@Incubating
public interface ScalaLibraryBinarySpec extends BinarySpec {

    /**
     * The set of tasks associated with this binary.
     */
    JvmBinaryTasks getTasks();

    /**
     * Returns the {@link org.gradle.runtime.jvm.toolchain.JavaToolChain} that will be used to build this binary.
     */
    ScalaToolChain getToolChain();

    /**
     * The classes directory for this binary.
     */
    File getClassesDir();

    /**
     * Sets the classes directory for this binary.
     */
    void setClassesDir(File classesDir);

    /**
     * The resources directory for this binary.
     */
    File getResourcesDir();

    /**
     * Sets the resources directory for this binary.
     */
    void setResourcesDir(File dir);
}