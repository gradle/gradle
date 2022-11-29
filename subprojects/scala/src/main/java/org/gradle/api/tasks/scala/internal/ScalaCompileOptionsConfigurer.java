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

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.VersionInfo;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.tasks.scala.ScalaCompileOptions;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.internal.JavaToolchain;

import java.io.File;
import java.util.Collections;
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
    private static final DefaultVersionComparator VERSION_COMPARATOR = new DefaultVersionComparator();

    public static void configure(ScalaCompileOptions scalaCompileOptions, JavaInstallationMetadata toolchain, Set<File> scalaClasspath, VersionParser versionParser) {
        if (toolchain == null) {
            return;
        }

        File scalaJar = ScalaRuntimeHelper.findScalaJar(scalaClasspath, "library");
        if (scalaJar == null) {
            return;
        }

        String scalaVersion = ScalaRuntimeHelper.getScalaVersion(scalaJar);
        if (scalaVersion == null) {
            return;
        }

        List<String> additionalParameters = scalaCompileOptions.getAdditionalParameters();
        if (additionalParameters != null && additionalParameters.stream().anyMatch(s -> s.startsWith("-target:"))) {
            return;
        }

        String targetParameter = determineTargetParameter(scalaVersion, (JavaToolchain) toolchain, versionParser);
        if (additionalParameters == null) {
            scalaCompileOptions.setAdditionalParameters(Collections.singletonList(targetParameter));
        } else {
            additionalParameters.add(targetParameter);
        }
    }

    private static String determineTargetParameter(String scalaVersion, JavaToolchain javaToolchain, VersionParser versionParser) {
        int effectiveTarget = javaToolchain.isFallbackToolchain() ? FALLBACK_JVM_TARGET : javaToolchain.getLanguageVersion().asInt();
        VersionInfo currentScalaVersion = new VersionInfo(versionParser.transform(scalaVersion));
        boolean scalaBefore2131 = VERSION_COMPARATOR.compare(currentScalaVersion, new VersionInfo(versionParser.transform("2.13.1"))) < 0;
        if (scalaBefore2131) {
            return String.format("-target:jvm-1.%s", effectiveTarget);
        } else {
            return String.format("-target:%s", effectiveTarget);
        }
    }
}
