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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Configures scala compile options to include the proper jvm target.
 * Gradle tool is not responsible for understanding if the version of Scala selected is compatible with the toolchain,
 * except for rejecting toolchains older than Java 17 for Scala 3.8 and later.
 * Does not configure if Java Toolchain feature is not in use
 *
 * @since 7.3
 */
@NullMarked
public class ScalaCompileOptionsConfigurer {

    private static final int FALLBACK_JVM_TARGET = 8;
    private static final int JAVA_17_FALLBACK_JVM_TARGET = 17;
    private static final int MAX_LEGACY_JVM_TARGET = 8;

    /**
     * Support for these flags in different minor releases of Scala varies,
     * but we need to detect as many variants as possible to avoid overriding the target or release.
     */
    private static final List<String> TARGET_DEFINING_PARAMETERS = Arrays.asList(
        // Scala 2
        "-target", "--target",
        // Scala 2 and 3
        "-release", "--release",
        // Scala 3
        "-java-output-version", "--java-output-version",
        "-Xunchecked-java-output-version", "--Xunchecked-java-output-version",
        "-Xtarget", "--Xtarget"
    );

    private static final VersionNumber RELEASE_SUPPORTED_SINCE_VERSION = VersionNumber.parse("2.12.5");
    private static final VersionNumber SCALA_3_FIRST_VERSION = VersionNumber.parse("3.0.0");

    private static final VersionNumber SCALA_2_13_FIRST_VERSION = VersionNumber.parse("2.13.0");
    private static final VersionNumber PLAIN_TARGET_FORMAT_IN_2_13_SINCE_VERSION = VersionNumber.parse("2.13.1");
    private static final VersionNumber RELEASE_REPLACES_TARGET_IN_2_13_SINCE_VERSION = VersionNumber.parse("2.13.9");

    private static final VersionNumber PLAIN_TARGET_FORMAT_IN_2_12_SINCE_VERSION = VersionNumber.parse("2.12.16");
    private static final VersionNumber RELEASE_REPLACES_TARGET_IN_2_12_SINCE_VERSION = VersionNumber.parse("2.12.17");

    public static void configure(ScalaCompileOptions scalaCompileOptions, @Nullable JavaInstallationMetadata toolchain, Set<File> scalaClasspath) {
        if (toolchain == null) {
            return;
        }

        VersionNumber scalaVersion = ScalaJavaCompatibility.findScalaVersion(scalaClasspath);
        if (VersionNumber.UNKNOWN.equals(scalaVersion)) {
            return;
        }

        JavaToolchain javaToolchain = (JavaToolchain) toolchain;
        ScalaJavaCompatibility.assertCanRunOn(scalaVersion, javaToolchain.getLanguageVersion().asInt());

        if (hasTargetDefiningParameter(scalaCompileOptions.getAdditionalParameters())) {
            return;
        }

        List<String> targetParameters = determineTargetParameters(scalaVersion.getBaseVersion(), javaToolchain);
        scalaCompileOptions.getAdditionalParameters().addAll(targetParameters);
    }

    private static boolean hasTargetDefiningParameter(List<String> additionalParameters) {
        return additionalParameters.stream()
            .anyMatch(s -> TARGET_DEFINING_PARAMETERS.stream().anyMatch(param -> param.equals(s) || s.startsWith(param + ":")));
    }

    /**
     * Computes parameters to specify how Scala should handle Java APIs and produced bytecode version.
     * <p>
     * The exact result depends on the Scala version in use and if the toolchain is user specified or not.
     * Pre-releases are expected to accept the same parameters as their final release.
     * Early milestones may not, if the parameter format changed during their release cycle.
     *
     * @param scalaVersion The detected scala version, without qualifier
     * @param javaToolchain The toolchain used to run compilation
     * @return Scala compiler parameters
     */
    private static List<String> determineTargetParameters(VersionNumber scalaVersion, JavaToolchain javaToolchain) {
        boolean explicitToolchain = !javaToolchain.isFallbackToolchain();
        int fallbackTarget = ScalaJavaCompatibility.requiresJava17(scalaVersion) ? JAVA_17_FALLBACK_JVM_TARGET : FALLBACK_JVM_TARGET;
        int effectiveTarget = !explicitToolchain ? fallbackTarget : javaToolchain.getLanguageVersion().asInt();
        switch (getTargetParameterFormat(scalaVersion)) {
            case RELEASE_OR_XTARGET:
                return Collections.singletonList(explicitToolchain
                    ? String.format("-release:%s", effectiveTarget)
                    : String.format("-Xtarget:%s", effectiveTarget));
            case RELEASE_OR_PLAIN_TARGET:
                return Collections.singletonList(explicitToolchain
                    ? String.format("-release:%s", effectiveTarget)
                    : String.format("-target:%s", effectiveTarget));
            case PLAIN_TARGET:
                return Collections.singletonList(String.format("-target:%s", effectiveTarget));
            case LEGACY_TARGET:
                if (effectiveTarget <= MAX_LEGACY_JVM_TARGET) {
                    return Collections.singletonList(String.format("-target:jvm-1.%s", effectiveTarget));
                }
                // Emit the highest supported bytecode, compiling against the toolchain's API where possible.
                // These versions only accept "-release N" as two arguments, and reject "-release:N".
                String legacyTarget = String.format("-target:jvm-1.%s", MAX_LEGACY_JVM_TARGET);
                if (scalaVersion.compareTo(RELEASE_SUPPORTED_SINCE_VERSION) >= 0) {
                    return Arrays.asList(legacyTarget, "-release", String.valueOf(effectiveTarget));
                }
                return Collections.singletonList(legacyTarget);
            default:
                throw new IllegalStateException("Unknown target parameter format");
        }
    }

    private static TargetParameterFormat getTargetParameterFormat(VersionNumber scalaVersion) {
        if (scalaVersion.compareTo(SCALA_3_FIRST_VERSION) >= 0) {
            return TargetParameterFormat.RELEASE_OR_XTARGET;
        }
        if (scalaVersion.compareTo(SCALA_2_13_FIRST_VERSION) >= 0) {
            if (scalaVersion.compareTo(RELEASE_REPLACES_TARGET_IN_2_13_SINCE_VERSION) >= 0) {
                return TargetParameterFormat.RELEASE_OR_PLAIN_TARGET;
            }
            if (scalaVersion.compareTo(PLAIN_TARGET_FORMAT_IN_2_13_SINCE_VERSION) >= 0) {
                return TargetParameterFormat.PLAIN_TARGET;
            }
            return TargetParameterFormat.LEGACY_TARGET;
        }
        if (scalaVersion.compareTo(RELEASE_REPLACES_TARGET_IN_2_12_SINCE_VERSION) >= 0) {
            return TargetParameterFormat.RELEASE_OR_PLAIN_TARGET;
        }
        if (scalaVersion.compareTo(PLAIN_TARGET_FORMAT_IN_2_12_SINCE_VERSION) >= 0) {
            return TargetParameterFormat.PLAIN_TARGET;
        }
        return TargetParameterFormat.LEGACY_TARGET;
    }

    private enum TargetParameterFormat {
        /** {@code -target:jvm-1.N}, accepting N up to 8 */
        LEGACY_TARGET,
        /** {@code -target:N} */
        PLAIN_TARGET,
        /** {@code -release:N}, or {@code -target:N} for the fallback toolchain */
        RELEASE_OR_PLAIN_TARGET,
        /** {@code -release:N}, or {@code -Xtarget:N} for the fallback toolchain */
        RELEASE_OR_XTARGET
    }
}
