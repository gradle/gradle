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
package org.gradle.tooling.internal.consumer;

import org.gradle.api.internal.DefaultClassPathProvider;
import org.gradle.api.tasks.wrapper.Wrapper;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.util.DistributionLocator;
import org.gradle.util.GradleVersion;
import org.gradle.util.UncheckedException;
import org.gradle.wrapper.Download;
import org.gradle.wrapper.Install;
import org.gradle.wrapper.PathAssembler;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Set;

public class DistributionFactory {
    public static final String USE_CLASSPATH_AS_DISTRIBUTION = "org.gradle.useClasspathAsDistribution";
    private final File userHomeDir;

    public DistributionFactory(File userHomeDir) {
        this.userHomeDir = userHomeDir;
    }

    public Distribution getCurrentDistribution() {
        if ("true".equalsIgnoreCase(System.getProperty(USE_CLASSPATH_AS_DISTRIBUTION))) {
            return new ClasspathDistribution();
        }

        return getDownloadedDistribution(GradleVersion.current().getVersion());
    }

    public Distribution getDistribution(final File gradleHomeDir) {
        return new InstalledDistribution(gradleHomeDir, String.format("Gradle installation '%s'", gradleHomeDir), String.format("Gradle installation directory '%s'", gradleHomeDir));
    }

    public Distribution getDistribution(String gradleVersion) {
        if (gradleVersion.equals(GradleVersion.current().getVersion())) {
            return getCurrentDistribution();
        }
        return getDownloadedDistribution(gradleVersion);
    }

    private Distribution getDownloadedDistribution(String gradleVersion) {
        URI distUri;
        try {
            distUri = new URI(new DistributionLocator().getDistributionFor(GradleVersion.version(gradleVersion)));
        } catch (URISyntaxException e) {
            throw UncheckedException.asUncheckedException(e);
        }
        return getDistribution(distUri);
    }

    public Distribution getDistribution(URI gradleDistribution) {
        File installDir;
        try {
            Install install = new Install(false, false, new Download(), new PathAssembler(userHomeDir));
            installDir = install.createDist(gradleDistribution, PathAssembler.GRADLE_USER_HOME_STRING, Wrapper.DEFAULT_DISTRIBUTION_PARENT_NAME, PathAssembler.GRADLE_USER_HOME_STRING, Wrapper.DEFAULT_DISTRIBUTION_PARENT_NAME);
        } catch (Exception e) {
            throw new GradleConnectionException(String.format("Could not install Gradle distribution from '%s'.", gradleDistribution), e);
        }
        return new InstalledDistribution(installDir, String.format("Gradle distribution '%s'", gradleDistribution), String.format("Gradle distribution '%s'", gradleDistribution));
    }

    private static class InstalledDistribution implements Distribution {
        private final File gradleHomeDir;
        private final String displayName;
        private final String locationDisplayName;

        public InstalledDistribution(File gradleHomeDir, String displayName, String locationDisplayName) {
            this.gradleHomeDir = gradleHomeDir;
            this.displayName = displayName;
            this.locationDisplayName = locationDisplayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Set<File> getToolingImplementationClasspath() {
            if (!gradleHomeDir.exists()) {
                throw new IllegalArgumentException(String.format("The specified %s does not exist.", locationDisplayName));
            }
            if (!gradleHomeDir.isDirectory()) {
                throw new IllegalArgumentException(String.format("The specified %s is not a directory.", locationDisplayName));
            }
            File libDir = new File(gradleHomeDir, "lib");
            if (!libDir.isDirectory()) {
                throw new IllegalArgumentException(String.format("The specified %s does not appear to contain a Gradle distribution.", locationDisplayName));
            }
            Set<File> files = new LinkedHashSet<File>();
            for (File file : libDir.listFiles()) {
                if (file.getName().endsWith(".jar")) {
                    files.add(file);
                }
            }
            return files;
        }
    }

    private static class ClasspathDistribution implements Distribution {
        public String getDisplayName() {
            return "Gradle classpath distribution";
        }

        public Set<File> getToolingImplementationClasspath() {
            DefaultClassPathProvider provider = new DefaultClassPathProvider();
            return provider.findClassPath("GRADLE_RUNTIME");
        }
    }
}
