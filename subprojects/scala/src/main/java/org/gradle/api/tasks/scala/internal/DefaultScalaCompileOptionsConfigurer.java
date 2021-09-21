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
import org.gradle.api.tasks.ScalaRuntime;
import org.gradle.api.tasks.scala.ScalaCompileOptions;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;

import java.io.File;
import java.util.Collections;
import java.util.Set;

/**
 * Configures scala compile options to include the proper jvm target.
 * Does not configure if Java Toolchain feature is not in use
 * @since 7.3
 */
public class DefaultScalaCompileOptionsConfigurer implements ScalaCompileOptionsConfigurer {

    @Override
    public void configure(ScalaCompileOptions scalaCompileOptions, JavaInstallationMetadata toolchain, Set<File> scalaClasspath) {
        if (toolchain == null) {
            return;
        }

        File scalaJar = ScalaRuntime.findScalaJar(scalaClasspath, "library");
        if(scalaJar == null) {
            return;
        }

        String scalaVersion = ScalaRuntime.getScalaVersion(scalaJar);
        if(scalaVersion == null) {
            return;
        }

        if(scalaCompileOptions.getAdditionalParameters() != null && scalaCompileOptions.getAdditionalParameters().stream().anyMatch(s -> s.startsWith("-target:"))) {
            return;
        }

        Integer jvmVersion = toolchain.getLanguageVersion().asInt();
        String targetParameter = determineTargetParameter(scalaVersion, jvmVersion);
        if(scalaCompileOptions.getAdditionalParameters() == null) {
            scalaCompileOptions.setAdditionalParameters(Collections.singletonList(targetParameter));
        } else {
            scalaCompileOptions.getAdditionalParameters().add(targetParameter);
        }
    }

    private String determineTargetParameter(String scalaVersion, Integer jvmVersion) {
        if(jvmVersion < 8) {
            return String.format("-target:jvm-1.%s", jvmVersion);
        }

        VersionParser versionParser = new VersionParser();
        DefaultVersionComparator comparator = new DefaultVersionComparator();
        int comparisonResult = comparator.compare(new VersionInfo(versionParser.transform(scalaVersion)), new VersionInfo(versionParser.transform("2.13.1")));
        if(comparisonResult < 0) {
            return "-target:jvm-1.8";
        }

        return String.format("-target:%s", jvmVersion);
    }
}
