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
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.time.Clock;
import org.gradle.tooling.BuildCancelledException;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener;
import org.gradle.util.internal.DistributionLocator;
import org.gradle.util.GradleVersion;
import org.gradle.wrapper.GradleUserHomeLookup;
import org.gradle.wrapper.SystemPropertiesHandler;
import org.gradle.wrapper.WrapperConfiguration;
import org.gradle.wrapper.WrapperExecutor;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;

import static org.gradle.internal.FileUtils.hasExtension;

public class DistributionFactory {
    private final Clock clock;

    public DistributionFactory(Clock clock) {
        this.clock = clock;
    }

    /**
     * Returns the default distribution to use for the specified project.
     */
    public Distribution getDefaultDistribution(File projectDir, boolean searchUpwards) {
        BuildLayout layout = new BuildLayoutFactory().getLayoutFor(projectDir, searchUpwards);
        WrapperExecutor wrapper = WrapperExecutor.forProjectDirectory(layout.getRootDirectory());
        if (wrapper.getDistribution() != null) {
            return new ZippedDistribution(wrapper.getConfiguration(), clock);
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
        return new ZippedDistribution(configuration, clock);
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
        private final Clock clock;

        private ZippedDistribution(WrapperConfiguration wrapperConfiguration, Clock clock) {
            this.wrapperConfiguration = wrapperConfiguration;
            this.clock = clock;
        }

        @Override
        public String getDisplayName() {
            return "Gradle distribution '" + wrapperConfiguration.getDistribution() + "'";
        }

        @Override
        public ClassPath getToolingImplementationClasspath(ProgressLoggerFactory progressLoggerFactory, final InternalBuildProgressListener progressListener, final ConnectionParameters connectionParameters, BuildCancellationToken cancellationToken) {
            if (installedDistribution == null) {
                final DistributionInstaller installer = new DistributionInstaller(progressLoggerFactory, progressListener, clock);
                File installDir;
                try {
                    cancellationToken.addCallback(new Runnable() {
                        @Override
                        public void run() {
                            installer.cancel();
                        }
                    });
                    installDir = installer.install(determineRealUserHomeDir(connectionParameters), determineRootDir(connectionParameters), wrapperConfiguration, determineSystemProperties(connectionParameters));
                } catch (CancellationException e) {
                    throw new BuildCancelledException(String.format("Distribution download cancelled. Using distribution from '%s'.", wrapperConfiguration.getDistribution()), e);
                } catch (FileNotFoundException e) {
                    throw new IllegalArgumentException(String.format("The specified %s does not exist.", getDisplayName()), e);
                } catch (Exception e) {
                    throw new GradleConnectionException(String.format("Could not install Gradle distribution from '%s'.", wrapperConfiguration.getDistribution()), e);
                }
                installedDistribution = new InstalledDistribution(installDir, getDisplayName(), getDisplayName());
            }
            return installedDistribution.getToolingImplementationClasspath(progressLoggerFactory, progressListener, connectionParameters, cancellationToken);
        }

        private Map<String, String> determineSystemProperties(ConnectionParameters connectionParameters) {
            Map<String, String> systemProperties = new HashMap<String, String>();
            for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
                systemProperties.put(entry.getKey().toString(), entry.getValue() == null ? null : entry.getValue().toString());
            }
            systemProperties.putAll(SystemPropertiesHandler.getSystemProperties(new File(determineRootDir(connectionParameters), "gradle.properties")));
            systemProperties.putAll(SystemPropertiesHandler.getSystemProperties(new File(determineRealUserHomeDir(connectionParameters), "gradle.properties")));
            return systemProperties;
        }

        private File determineRootDir(ConnectionParameters connectionParameters) {
            return new BuildLayoutFactory().getLayoutFor(
                connectionParameters.getProjectDir(),
                connectionParameters.isSearchUpwards() != null ? connectionParameters.isSearchUpwards() : true
            ).getRootDirectory();
        }

        private File determineRealUserHomeDir(ConnectionParameters connectionParameters) {
            File distributionBaseDir = connectionParameters.getDistributionBaseDir();
            if (distributionBaseDir != null) {
                return distributionBaseDir;
            }
            File userHomeDir = connectionParameters.getGradleUserHomeDir();
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

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public ClassPath getToolingImplementationClasspath(ProgressLoggerFactory progressLoggerFactory, InternalBuildProgressListener progressListener, ConnectionParameters connectionParameters, BuildCancellationToken cancellationToken) {
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
            return DefaultClassPath.of(files);
        }
    }

    private static class ClasspathDistribution implements Distribution {
        @Override
        public String getDisplayName() {
            return "Gradle classpath distribution";
        }

        @Override
        public ClassPath getToolingImplementationClasspath(ProgressLoggerFactory progressLoggerFactory, InternalBuildProgressListener progressListener, ConnectionParameters connectionParameters, BuildCancellationToken cancellationToken) {
            DefaultModuleRegistry registry = new DefaultModuleRegistry(null);
            return registry.getModule("gradle-tooling-api-provider").getAllRequiredModulesClasspath();
        }
    }
}
