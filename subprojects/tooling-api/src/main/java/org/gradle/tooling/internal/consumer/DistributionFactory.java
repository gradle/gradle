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
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.util.DistributionLocator;
import org.gradle.util.GradleVersion;
import org.gradle.util.UncheckedException;
import org.gradle.wrapper.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Set;

public class DistributionFactory {
    private final File userHomeDir;
    private final ProgressLoggerFactory progressLoggerFactory;

    public DistributionFactory(File userHomeDir, ProgressLoggerFactory progressLoggerFactory) {
        this.userHomeDir = userHomeDir;
        this.progressLoggerFactory = progressLoggerFactory;
    }

    /**
     * Returns the default distribution to use for the specified project.
     */
    public Distribution getDefaultDistribution(File projectDir) {
        Wrapper wrapper = new Wrapper(projectDir);
        if (wrapper.getDistribution() != null) {
            return getDistribution(wrapper.getDistribution());
        }
        return getDownloadedDistribution(GradleVersion.current().getVersion());
    }

    /**
     * Returns the distribution installed in the specified directory.
     */
    public Distribution getDistribution(File gradleHomeDir) {
        return new InstalledDistribution(gradleHomeDir, String.format("Gradle installation '%s'", gradleHomeDir), String.format("Gradle installation directory '%s'", gradleHomeDir));
    }

    /**
     * Returns the distribution for the specified gradle version.
     */
    public Distribution getDistribution(String gradleVersion) {
        return getDownloadedDistribution(gradleVersion);
    }

    /**
     * Returns the distribution at the given URI.
     */
    public Distribution getDistribution(URI gradleDistribution) {
        return new ZippedDistribution(gradleDistribution);
    }

    /**
     * Uses the classpath to locate the distribution.
     */
    public Distribution getClasspathDistribution() {
        return new ClasspathDistribution();
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

    private class ZippedDistribution implements Distribution {
        private final URI gradleDistribution;
        private InstalledDistribution installedDistribution;

        private ZippedDistribution(URI gradleDistribution) {
            this.gradleDistribution = gradleDistribution;
        }

        public String getDisplayName() {
            return String.format("Gradle distribution '%s'", gradleDistribution);
        }

        public Set<File> getToolingImplementationClasspath() {
            if (installedDistribution == null) {
                File installDir;
                try {
                    Install install = new Install(false, false, new ProgressReportingDownload(progressLoggerFactory), new PathAssembler(userHomeDir));
                    installDir = install.createDist(gradleDistribution, PathAssembler.GRADLE_USER_HOME_STRING, org.gradle.api.tasks.wrapper.Wrapper.DEFAULT_DISTRIBUTION_PARENT_NAME, PathAssembler.GRADLE_USER_HOME_STRING, org.gradle.api.tasks.wrapper.Wrapper.DEFAULT_DISTRIBUTION_PARENT_NAME);
                } catch (FileNotFoundException e) {
                    throw new IllegalArgumentException(String.format("The specified %s does not exist.", getDisplayName()), e);
                } catch (Exception e) {
                    throw new GradleConnectionException(String.format("Could not install Gradle distribution from '%s'.", gradleDistribution), e);
                }
                installedDistribution = new InstalledDistribution(installDir, getDisplayName(), getDisplayName());
            }
            return installedDistribution.getToolingImplementationClasspath();
        }
    }

    private static class ProgressReportingDownload implements IDownload {
        private final ProgressLoggerFactory progressLoggerFactory;

        private ProgressReportingDownload(ProgressLoggerFactory progressLoggerFactory) {
            this.progressLoggerFactory = progressLoggerFactory;
        }

        public void download(URI address, File destination) throws Exception {
            ProgressLogger progressLogger = progressLoggerFactory.newOperation(DistributionFactory.class);
            progressLogger.setDescription(String.format("Download %s", address));
            progressLogger.started();
            try {
                new Download().download(address, destination);
            } finally {
                progressLogger.completed();
            }
        }
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
