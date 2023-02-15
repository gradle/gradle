/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.InvalidUserDataException;
import org.gradle.internal.deprecation.DeprecationLogger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;

/**
 * Utility class for resolving and validating string paths pointing to
 * Java executables.
 */
public class JavaExecutableUtils {

    public static File resolveExecutable(String executable) {
        File executableFile = new File(executable);
        if (!executableFile.isAbsolute()) {
            DeprecationLogger.deprecateBehaviour("Configuring a Java executable via a relative path.")
                    .withContext("Resolving relative file paths might yield unexpected results, there is no single clear location it would make sense to resolve against.")
                    .withAdvice("Configure an absolute path to a Java executable instead.")
                    .willBecomeAnErrorInGradle9()
                    .undocumented()
                    .nagUser();
        }
        if (!executableFile.getAbsoluteFile().exists()) {
            throw new InvalidUserDataException("The configured executable does not exist (" + executableFile.getAbsolutePath() + ")");
        }
        if (executableFile.getAbsoluteFile().isDirectory()) {
            throw new InvalidUserDataException("The configured executable is a directory (" + executableFile.getAbsolutePath() + ")");
        }
        return executableFile;
    }

    public static void validateExecutable(@Nullable String executable, String executableDescription, File referenceFile, String referenceDescription) {
        if (executable == null) {
            return;
        }

        File executableFile = resolveExecutable(executable);
        if (executableFile.equals(referenceFile)) {
            return;
        }

        File canonicalExecutableFile = canonicalFile(executableFile);
        File canonicalReferenceFile = canonicalFile(referenceFile);
        if (canonicalExecutableFile.equals(canonicalReferenceFile)) {
            return;
        }

        throw new IllegalStateException(executableDescription + " does not match " + referenceDescription + ".");
    }

    private static File canonicalFile(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            throw new RuntimeException("Can't resolve canonical path of file " + file, e);
        }
    }

}
