/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.tooling.internal.consumer;

import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import org.gradle.api.GradleException;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class DefaultProviderClasspathUpdater implements ProviderClasspathUpdater {
    private static ByteSource createPatchSource() {
        URL resource = DistributionFactory.class.getResource("gradle-tooling-api-provider-patch.jar");
        return Resources.asByteSource(resource);
    }
    private File patchJar;

    public Iterable<File> prependToClasspath(File libDir) {
        if (shouldAddPatches(libDir)) {
            return Collections.singletonList(getPatchJar());
        }
        return Collections.emptyList();
    }

    private synchronized File getPatchJar() {
        if (patchJar == null) {
            try {
                File patchJarFile = File.createTempFile("gradle-tooling-patch", ".jar");
                createPatchSource().copyTo(Files.asByteSink(patchJarFile));
                patchJarFile.deleteOnExit();
                patchJar = patchJarFile;
            } catch (IOException ioe) {
                throw new GradleException("Cannot configure patch for Gradle Tooling API provider", ioe);
            }
        }
        return patchJar;
    }

    private boolean shouldAddPatches(File libDir) {
        for (File file : libDir.listFiles()) {
            if (file.getName().startsWith("gradle-core") && file.getName().endsWith(".jar")) {
                try {
                    JarFile jar = new JarFile(file);
                    JarEntry entry = jar.getJarEntry("org/gradle/build-receipt.properties");
                    if (entry == null) {
                        continue;
                    }
                    Properties properties = new Properties();
                    properties.load(jar.getInputStream(entry));
                    String version = properties.getProperty("versionBase");
                    if (version == null) {
                        continue;
                    }
                    if (GradleVersion.version(version).compareTo(GradleVersion.version("1.8")) >= 0
                            && GradleVersion.version(version).compareTo(GradleVersion.version("2.2")) <= 0) {
                        return true;
                    }
                } catch (IOException ioe) {
                    // ignore
                }
            }
        }
        return false;

    }
}
