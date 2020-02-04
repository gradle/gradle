/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.fixtures.jvm

import org.gradle.api.JavaVersion
import org.gradle.internal.nativeintegration.filesystem.FileCanonicalizer

import java.util.regex.Matcher
import java.util.regex.Pattern


/**
 * Locates JVMs installed using sdkman.
 * See https://sdkman.io/
 */
class SdkManJvmLocator {

    private static final Pattern JDKMAN_SDK_DIR = Pattern.compile("(\\d+\\.\\d+\\.\\d+(_\\d+)?).*")
    private static final String SDKMAN_CANDIDATES_DIR_ENV_NAME = "SDKMAN_CANDIDATES_DIR"
    private final FileCanonicalizer fileCanonicalizer

    SdkManJvmLocator(FileCanonicalizer fileCanonicalizer) {
        this.fileCanonicalizer = fileCanonicalizer
    }

    List<JvmInstallation> findJvms() {
        List<JvmInstallation> jvms = new ArrayList<>()
        String sdkManCandidatesDirPath = System.getenv(SDKMAN_CANDIDATES_DIR_ENV_NAME)
        if (sdkManCandidatesDirPath == null) {
            return jvms
        }
        File javaCandidatesDir = new File(sdkManCandidatesDirPath, "java")
        if (!javaCandidatesDir.isDirectory()) {
            return jvms
        }
        Set<File> javaHomes = new HashSet<>()
        for (File file : javaCandidatesDir.listFiles()) {
            Matcher matcher = JDKMAN_SDK_DIR.matcher(file.getName())
            if (!matcher.matches()) {
                continue
            }
            File javaHome = fileCanonicalizer.canonicalize(file)
            if (!javaHomes.add(javaHome)) {
                continue
            }
            if (!new File(file, "bin/javac").isFile()) {
                continue
            }
            String version = matcher.group(1)
            jvms.add(new JvmInstallation(JavaVersion.toVersion(version), version, file, true, JvmInstallation.Arch.Unknown))
        }
        return jvms;
    }
}
