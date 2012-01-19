/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.util.internal;

import org.gradle.internal.nativeplatform.OperatingSystem;

import java.io.File;
import java.io.IOException;

/**
 * Cut down from the ant-based implementation. Removed workarounds for exotic jres, etc.
 * <p>
 * Tries jdk, jre and if not found, fails. Fails even if the executable is on PATH!!!
 * <p>
 * by Szczepan Faber, created at: 1/19/12
 */
public class StrictJavaLocaliser implements JavaExecutableFinder {

    private File javaHome;

    public StrictJavaLocaliser(File javaHome) {
        if (javaHome == null || !javaHome.isDirectory()) {
            throw new IllegalArgumentException("Supplied javaHome must be a valid directory. You supplied: " + javaHome);
        }
        this.javaHome = javaHome;
    }

    /**
     * {@inheritDoc}
     */
    public String getJavaExecutable(String command) {
        File maybeJdk = new File(javaHome, "../bin");
        String executable = findInDir(maybeJdk, command);
        if (executable != null) {
            return executable;
        }

        File maybeJre = new File(javaHome, "/bin");
        executable = findInDir(maybeJre, command);

        if (executable != null) {
            return executable;
        }

        throw new JavaExecutableNotFoundException(
                "Unable to find java executable: " + command + " under supplied java home: " + javaHome
                + "\n Tried: " + maybeJdk
                + "\n Tried: " + maybeJre);
    }

    /**
     * @throws JavaExecutableNotFoundException when supplied javaHome does not contain 'java' executable
     */
    public void validate() throws JavaExecutableNotFoundException {
        getJavaExecutable("java");
    }

    /**
     * Look for an executable in a given directory.
     *
     * @return null if the executable cannot be found.
     */
    String findInDir(File theDir, String command) {
        File dir;
        try {
            dir = theDir.getCanonicalFile();
        } catch (IOException e) {
            throw new JavaExecutableNotFoundException(
                    "Unable to find java executable: " + command + " in dir: " + theDir
                    + ". It seems the dir is incorrect.", e);
        }
        if (!dir.exists()) {
            return null;
        }

        File executable = new File(dir, addExtension(command));
        if (!executable.exists()) {
            return null;
        }

        return executable.getAbsolutePath();
    }

    /**
     * Adds a system specific extension to the name of an executable.
     */
    static String addExtension(String command) {
        return command + (OperatingSystem.current().isWindows() ? ".exe" : "");
    }

    public static class JavaExecutableNotFoundException extends RuntimeException {
        public JavaExecutableNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }

        public JavaExecutableNotFoundException(String message) {
            super(message);
        }
    }
}
