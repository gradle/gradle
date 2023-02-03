/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.tasks.scala.ScalaCompileOptions;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.internal.JavaToolchain;
import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Configures scala compile options to include the proper jvm target.
 * Gradle tool is not responsible for understanding if the version of Scala selected is compatible with the toolchain
 * Does not configure if Java Toolchain feature is not in use
 *
 * @since 7.3
 */
public class ScalaCompileOptionsConfigurer {

    private static final int FALLBACK_JVM_TARGET = 8;
    private static final List<String> TARGET_DEFINING_PARAMETERS = Arrays.asList(
        // Scala 2
        "-target:", "--target:",
        // Scala 2 and 3
        "-release:", "--release:",
        // Scala 3
        "-Xtarget:", "-java-output-version:", "-Xunchecked-java-output-version:"
    );

    private static final VersionNumber PLAIN_TARGET_FORMAT_SINCE_VERSION = VersionNumber.parse("2.13.1");
    private static final VersionNumber RELEASE_REPLACES_TARGET_SINCE_VERSION = VersionNumber.parse("2.13.9");

    public static void configure(ScalaCompileOptions scalaCompileOptions, JavaInstallationMetadata toolchain, Set<File> scalaClasspath) {
        if (toolchain == null) {
            return;
        }

        // When Scala 3 is used it appears on the classpath together with Scala 2
        File scalaJar = ScalaRuntimeHelper.findScalaJar(scalaClasspath, "library_3");
        if (scalaJar == null) {
            scalaJar = ScalaRuntimeHelper.findScalaJar(scalaClasspath, "library");
            if (scalaJar == null) {
                return;
            }
        }

        VersionNumber scalaVersion = VersionNumber.parse(ScalaRuntimeHelper.getScalaVersion(scalaJar));
        if (VersionNumber.UNKNOWN.equals(scalaVersion)) {
            return;
        }

        if (hasTargetDefiningParameter(scalaCompileOptions.getAdditionalParameters())) {
            return;
        }

        String targetParameter = determineTargetParameter(scalaVersion, (JavaToolchain) toolchain);
        scalaCompileOptions.getAdditionalParameters().add(targetParameter);
    }

    private static boolean hasTargetDefiningParameter(List<String> additionalParameters) {
        return additionalParameters.stream().anyMatch(s -> TARGET_DEFINING_PARAMETERS.stream().anyMatch(s::startsWith));
    }

    private static String determineTargetParameter(VersionNumber scalaVersion, JavaToolchain javaToolchain) {
        int effectiveTarget = javaToolchain.isFallbackToolchain() ? FALLBACK_JVM_TARGET : javaToolchain.getLanguageVersion().asInt();
        if (scalaVersion.compareTo(RELEASE_REPLACES_TARGET_SINCE_VERSION) >= 0) {
            return String.format("-release:%s", effectiveTarget);
        } else if (scalaVersion.compareTo(PLAIN_TARGET_FORMAT_SINCE_VERSION) >= 0) {
            return String.format("-target:%s", effectiveTarget);
        } else {
            return String.format("-target:jvm-1.%s", effectiveTarget);
        }
    }
}
