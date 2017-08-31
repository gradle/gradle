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

import org.gradle.api.internal.classpath.DefaultModuleRegistry;
import org.gradle.api.internal.classpath.Module;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.time.TimeProvider;
import org.gradle.tooling.BuildCancelledException;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener;
import org.gradle.util.DistributionLocator;
import org.gradle.util.GradleVersion;
import org.gradle.wrapper.GradleUserHomeLookup;
import org.gradle.wrapper.WrapperConfiguration;
import org.gradle.wrapper.WrapperExecutor;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.CancellationException;

import static org.gradle.internal.FileUtils.hasExtension;

public class DistributionFactory {
    private final TimeProvider timeProvider;
    private File distributionBaseDir;

    public DistributionFactory(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    public void setDistributionBaseDir(File distributionBaseDir) {
        this.distributionBaseDir = distributionBaseDir;
    }

    /**
     * Returns the default distribution to use for the specified project.
     */
    public Distribution getDefaultDistribution(File projectDir, boolean searchUpwards) {
        BuildLayout layout = new BuildLayoutFactory().getLayoutFor(projectDir, searchUpwards);
        WrapperExecutor wrapper = WrapperExecutor.forProjectDirectory(layout.getRootDirectory());
        if (wrapper.getDistribution() != null) {
            return new ZippedDistribution(wrapper.getConfiguration(), distributionBaseDir, timeProvider);
        }
        return getDownloadedDistribution(GradleVersion.current().getVersion());
    }

    /**
     * Returns the distribution installed in the specified directory.
     */
    public Distribution getDistribution(File gradleHomeDir) {
        return new InstalledDistribution(gradleHomeDir, "Gradle installation '" + gradleHomeDir + "'",
                "Gradle installation directory '" + gradleHomeDir + "'");
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
        WrapperConfiguration configuration = new WrapperConfiguration();
        configuration.setDistribution(gradleDistribution);
        return new ZippedDistribution(configuration, distributionBaseDir, timeProvider);
    }

    /**
     * Uses the classpath to locate the distribution.
     */
    public Distribution getClasspathDistribution() {
        return new ClasspathDistribution();
    }

    private Distribution getDownloadedDistribution(String gradleVersion) {
        URI distUri = new DistributionLocator().getDistributionFor(GradleVersion.version(gradleVersion));
        return getDistribution(distUri);
    }

    private static class ZippedDistribution implements Distribution {
        private InstalledDistribution installedDistribution;
        private final WrapperConfiguration wrapperConfiguration;
        private final File distributionBaseDir;
        private final TimeProvider timeProvider;

        private ZippedDistribution(WrapperConfiguration wrapperConfiguration, File distributionBaseDir, TimeProvider timeProvider) {
            this.wrapperConfiguration = wrapperConfiguration;
            this.distributionBaseDir = distributionBaseDir;
            this.timeProvider = timeProvider;
        }

        public String getDisplayName() {
            return "Gradle distribution '" + wrapperConfiguration.getDistribution() + "'";
        }

        public ClassPath getToolingImplementationClasspath(ProgressLoggerFactory progressLoggerFactory, final InternalBuildProgressListener progressListener, final File userHomeDir, BuildCancellationToken cancellationToken) {
            if (installedDistribution == null) {
                final DistributionInstaller installer = new DistributionInstaller(progressLoggerFactory, progressListener, timeProvider);
                File installDir;
                try {
                    cancellationToken.addCallback(new Runnable() {
                        public void run() {
                            installer.cancel();
                        }
                    });
                    installDir = installer.install(determineRealUserHomeDir(userHomeDir), wrapperConfiguration);
                } catch (CancellationException e) {
                    throw new BuildCancelledException(String.format("Distribution download cancelled. Using distribution from '%s'.", wrapperConfiguration.getDistribution()), e);
                } catch (FileNotFoundException e) {
                    throw new IllegalArgumentException(String.format("The specified %s does not exist.", getDisplayName()), e);
                } catch (Exception e) {
                    throw new GradleConnectionException(String.format("Could not install Gradle distribution from '%s'.", wrapperConfiguration.getDistribution()), e);
                }
                installedDistribution = new InstalledDistribution(installDir, getDisplayName(), getDisplayName());
            }
            return installedDistribution.getToolingImplementationClasspath(progressLoggerFactory, progressListener, userHomeDir, cancellationToken);
        }

        private File determineRealUserHomeDir(final File userHomeDir) {
            if(distributionBaseDir != null) {
                return distributionBaseDir;
            }

            return userHomeDir != null ? userHomeDir : GradleUserHomeLookup.gradleUserHome();
        }
    }

    private static class InstalledDistribution implements Distribution {
        private final File gradleHomeDir;
        private final String displayName;
        private final String locationDisplayName;

        InstalledDistribution(File gradleHomeDir, String displayName, String locationDisplayName) {
            this.gradleHomeDir = gradleHomeDir;
            this.displayName = displayName;
            this.locationDisplayName = locationDisplayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public ClassPath getToolingImplementationClasspath(ProgressLoggerFactory progressLoggerFactory, InternalBuildProgressListener progressListener, File userHomeDir, BuildCancellationToken cancellationToken) {
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
            File[] files = libDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return hasExtension(file, ".jar");
                }
            });
            // Make sure file order is always consistent
            Arrays.sort(files);
            return new DefaultClassPath(files);
        }
    }

    private static class ClasspathDistribution implements Distribution {
        public String getDisplayName() {
            return "Gradle classpath distribution";
        }

        public ClassPath getToolingImplementationClasspath(ProgressLoggerFactory progressLoggerFactory, InternalBuildProgressListener progressListener, File userHomeDir, BuildCancellationToken cancellationToken) {
            DefaultModuleRegistry registry = new DefaultModuleRegistry(null);
            ClassPath classpath = ClassPath.EMPTY;
            for (Module module : registry.getModule("gradle-launcher").getAllRequiredModules()) {
                classpath = classpath.plus(module.getClasspath());
            }
            return classpath;
        }
    }
}
