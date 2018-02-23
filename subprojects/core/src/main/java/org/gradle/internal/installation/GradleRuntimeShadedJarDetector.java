/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.installation;

import org.gradle.internal.UncheckedException;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class GradleRuntimeShadedJarDetector {

    private final static String FILE_PROTOCOL = "file";
    private final static String JAR_FILE_EXTENSION = ".jar";
    public final static String MARKER_FILENAME = "META-INF/.gradle-runtime-shaded";

    private GradleRuntimeShadedJarDetector() {
    }

    public static boolean isLoadedFrom(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Need to provide valid class reference");
        }

        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();

        if (codeSource != null) {
            URL location = codeSource.getLocation();

            if (isJarUrl(location)) {
                try {
                    return findMarkerFileInJar(new File(location.toURI()));
                } catch (URISyntaxException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        }

        return false;
    }

    private static boolean isJarUrl(URL location) {
        return location.getProtocol().equals(FILE_PROTOCOL) && location.getPath().endsWith(JAR_FILE_EXTENSION);
    }

    private static boolean findMarkerFileInJar(File jar) {
        JarFile jarFile = null;

        try {
            jarFile = new JarFile(jar);
            JarEntry markerFile = jarFile.getJarEntry(MARKER_FILENAME);

            if (markerFile != null) {
                return true;
            }
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (IOException e) {
                    //noinspection ThrowFromFinallyBlock
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        }

        return false;
    }
}
