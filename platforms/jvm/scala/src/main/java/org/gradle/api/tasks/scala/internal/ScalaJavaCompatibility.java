/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.tasks.scala.internal;

import org.gradle.api.InvalidUserDataException;
import org.gradle.util.internal.VersionNumber;
import org.jspecify.annotations.NullMarked;

import java.io.File;

/**
 * Java version requirements of the Scala tools that Gradle checks before running them.
 */
@NullMarked
public class ScalaJavaCompatibility {

    private static final VersionNumber SCALA_3_8_FIRST_VERSION = VersionNumber.parse("3.8.0");
    private static final int SCALA_3_8_MIN_JAVA_VERSION = 17;

    /**
     * Detects the Scala version from the Scala library on the given classpath.
     *
     * @return the Scala version, or {@link VersionNumber#UNKNOWN} if no Scala library is found
     */
    public static VersionNumber findScalaVersion(Iterable<File> scalaClasspath) {
        // When Scala 3 is used it appears on the classpath together with Scala 2
        File scalaJar = ScalaRuntimeHelper.findScalaJar(scalaClasspath, "library_3");
        if (scalaJar == null) {
            scalaJar = ScalaRuntimeHelper.findScalaJar(scalaClasspath, "library");
            if (scalaJar == null) {
                return VersionNumber.UNKNOWN;
            }
        }
        return VersionNumber.parse(ScalaRuntimeHelper.getScalaVersion(scalaJar));
    }

    /**
     * Returns whether the given Scala version requires Java 17, treating pre-releases like their final release.
     */
    public static boolean requiresJava17(VersionNumber scalaVersion) {
        return scalaVersion.getBaseVersion().compareTo(SCALA_3_8_FIRST_VERSION) >= 0;
    }

    /**
     * Fails early if the Scala tools cannot run on the given Java version, as no tool parameter can fix that.
     */
    public static void assertCanRunOn(VersionNumber scalaVersion, int javaVersion) {
        if (requiresJava17(scalaVersion) && javaVersion < SCALA_3_8_MIN_JAVA_VERSION) {
            throw new InvalidUserDataException(String.format(
                "Scala %s requires Java %d or later, but the Java toolchain is Java %d. Use a Java toolchain of version %d or later, or a Scala version before 3.8.",
                scalaVersion, SCALA_3_8_MIN_JAVA_VERSION, javaVersion, SCALA_3_8_MIN_JAVA_VERSION));
        }
    }
}
