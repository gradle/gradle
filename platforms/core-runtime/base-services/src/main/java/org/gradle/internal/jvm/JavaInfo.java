/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.jvm;

import javax.annotation.Nullable;
import java.io.File;

/**
 * Wraps a JAVA_HOME path to provide access to the various executables and tools
 * that are part of a JVM installation. The path wrapped by this info may not
 * necessarily represent a valid JVM installation, however attempts are made
 * to verify the installation's directory structure upon construction.
 */
public interface JavaInfo {

    /**
     * Get the JAVA_HOME path represented by this JavaInfo.
     */
    File getJavaHome();

    /**
     * @return The `java` executable for this JVM installation.
     *
     * @throws IllegalStateException If this JVM installation does not contain a `java` executable.
     */
    File getJavaExecutable();

    /**
     * @return The `javac` executable for this JVM installation.
     *
     * @throws IllegalStateException If this JVM installation does not contain a `javac` executable.
     */
    File getJavacExecutable();

    /**
     * @return The `javadoc` executable for this JVM installation.
     *
     * @throws IllegalStateException If this JVM installation does not contain a `javadoc` executable.
     */
    File getJavadocExecutable();

    /**
     * @return Get an executable from this JVM installation.
     *
     * @throws IllegalStateException If this JVM installation does not contain the executable.
     */
    File getExecutable(String name);

    /**
     * @return true if this JVM installation is a JDK installation or false if it represents a JRE installation.
     */
    boolean isJdk();

    /**
     * Get the tools.jar file for this JVM installation.
     *
     * @return null if this JVM installation does not contain a tools.jar file, for example if this
     * is a JRE installation or the JVM is newer than Java 8.
     */
    @Nullable
    File getToolsJar();

    /**
     * Get a JRE installation that corresponds to this JVM installation. The returned
     * JVM may either be embedded within the JDK installation or standalone, meaning
     * it is installed outside of this JDK installation.
     *
     * @return null if no JRE installation is present.
     */
    @Nullable
    File getJre();

    /**
     * Locates the JRE installation contained within this JVM installation.
     *
     * @return null if JRE is embedded within this JVM installation.
     */
    @Nullable
    File getEmbeddedJre();

    /**
     * Locates a standalone JRE installation that corresponds to this JVM installation.
     *
     * @return if there is no corresponding JRE installed parallel to this JVM installation.
     */
    @Nullable
    File getStandaloneJre();
}
