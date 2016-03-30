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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class GradleFatJar {

    public final static String MARKER_FILENAME = "META-INF/gradle-internal/fat.marker";

    public static boolean containsMarkerFile(File jar) {
        JarFile jarFile = null;

        try {
            jarFile = new JarFile(jar);
            JarEntry gradleImplDepsPropertyFile = jarFile.getJarEntry(MARKER_FILENAME);

            if (gradleImplDepsPropertyFile != null) {
                return true;
            }
        } catch (IOException e) {
            UncheckedException.throwAsUncheckedException(e);
        } finally {
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (IOException e) {
                    UncheckedException.throwAsUncheckedException(e);
                }
            }
        }

        return false;
    }
}
