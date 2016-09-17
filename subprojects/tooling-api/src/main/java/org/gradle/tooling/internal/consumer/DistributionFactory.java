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

import com.google.common.base.Preconditions;
import org.gradle.api.internal.classpath.DefaultModuleRegistry;
import org.gradle.api.internal.classpath.Module;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.tooling.BuildCancelledException;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.util.DistributionLocator;
import org.gradle.util.GradleVersion;
import org.gradle.wrapper.Download;
import org.gradle.wrapper.GradleUserHomeLookup;
import org.gradle.wrapper.IDownload;
import org.gradle.wrapper.Install;
import org.gradle.wrapper.Logger;
import org.gradle.wrapper.PathAssembler;
import org.gradle.wrapper.WrapperConfiguration;
import org.gradle.wrapper.WrapperExecutor;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.gradle.internal.FileUtils.hasExtension;

public class DistributionFactory {
    private final Factory<? extends ExecutorService> executorFactory;
    private File distributionBaseDir;

    public DistributionFactory(Factory<? extends ExecutorService> executorFactory) {
        this.executorFactory = Preconditions.checkNotNull(executorFactory);
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
            return new ZippedDistribution(wrapper.getConfiguration(), executorFactory, distributionBaseDir);
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
        return new ZippedDistribution(configuration, executorFactory, distributionBaseDir);
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
        private final Factory<? extends ExecutorService> executorFactory;
        private final File distributionBaseDir;

        private ZippedDistribution(WrapperConfiguration wrapperConfiguration, Factory<? extends ExecutorService> executorFactory, File distributionBaseDir) {
            this.wrapperConfiguration = wrapperConfiguration;
            this.executorFactory = executorFactory;
            this.distributionBaseDir = distributionBaseDir;
        }

        public String getDisplayName() {
            return "Gradle distribution '" + wrapperConfiguration.getDistribution() + "'";
        }

        public ClassPath getToolingImplementationClasspath(final ProgressLoggerFactory progressLoggerFactory, final File userHomeDir, BuildCancellationToken cancellationToken) {
            if (installedDistribution == null) {
                Callable<File> installDistroTask = new Callable<File>() {
                    public File call() throws Exception {
                        File installDir;
                        try {
                            File realUserHomeDir = determineRealUserHomeDir(userHomeDir);
                            Install install = new Install(new Logger(false), new ProgressReportingDownload(progressLoggerFactory), new PathAssembler(realUserHomeDir));
                            installDir = install.createDist(wrapperConfiguration);
                        } catch (FileNotFoundException e) {
                            throw new IllegalArgumentException(String.format("The specified %s does not exist.", getDisplayName()), e);
                        } catch (CancellationException e) {
                            throw new BuildCancelledException(String.format("Distribution download cancelled. Using distribution from '%s'.", wrapperConfiguration.getDistribution()), e);
                        } catch (Exception e) {
                            throw new GradleConnectionException(String.format("Could not install Gradle distribution from '%s'.", wrapperConfiguration.getDistribution()), e);
                        }
                        return installDir;
                    }
                };
                File installDir;
                ExecutorService executor = null;
                try {
                    executor = executorFactory.create();
                    final Future<File> installDirFuture = executor.submit(installDistroTask);
                    cancellationToken.addCallback(new Runnable() {
                        public void run() {
                            // TODO(radim): better to close the connection too to allow quick finish of the task
                            installDirFuture.cancel(true);
                        }
                    });
                    installDir = installDirFuture.get();
                } catch (CancellationException e) {
                    throw new BuildCancelledException(String.format("Distribution download cancelled. Using distribution from '%s'.", wrapperConfiguration.getDistribution()), e);
                } catch (InterruptedException e) {
                    throw new GradleConnectionException(String.format("Could not install Gradle distribution from '%s'.", wrapperConfiguration.getDistribution()), e);
                } catch (ExecutionException e) {
                    if (e.getCause() != null) {
                        UncheckedException.throwAsUncheckedException(e.getCause());
                    }
                    throw new GradleConnectionException(String.format("Could not install Gradle distribution from '%s'.", wrapperConfiguration.getDistribution()), e);
                } finally {
                    if (executor != null) {
                        executor.shutdown();
                    }
                }
                installedDistribution = new InstalledDistribution(installDir, getDisplayName(), getDisplayName());
            }
            return installedDistribution.getToolingImplementationClasspath(progressLoggerFactory, userHomeDir, cancellationToken);
        }

        private File determineRealUserHomeDir(final File userHomeDir) {
            if(distributionBaseDir != null) {
                return distributionBaseDir;
            }

            return userHomeDir != null ? userHomeDir : GradleUserHomeLookup.gradleUserHome();
        }
    }

    private static class ProgressReportingDownload implements IDownload {
        private final ProgressLoggerFactory progressLoggerFactory;

        private ProgressReportingDownload(ProgressLoggerFactory progressLoggerFactory) {
            this.progressLoggerFactory = progressLoggerFactory;
        }

        public void download(URI address, File destination) throws Exception {
            ProgressLogger progressLogger = progressLoggerFactory.newOperation(DistributionFactory.class);
            progressLogger.setDescription("Download " + address);
            progressLogger.started();
            try {
                new Download(new Logger(false), "Gradle Tooling API", GradleVersion.current().getVersion()).download(address, destination);
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

        public ClassPath getToolingImplementationClasspath(ProgressLoggerFactory progressLoggerFactory, File userHomeDir, BuildCancellationToken cancellationToken) {
            ProgressLogger progressLogger = progressLoggerFactory.newOperation(DistributionFactory.class);
            progressLogger.setDescription("Validate distribution");
            progressLogger.started();
            try {
                return getToolingImpl();
            } finally {
                progressLogger.completed();
            }
        }

        private ClassPath getToolingImpl() {
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
            LinkedHashSet<File> files = new LinkedHashSet<File>();
            for (File file : libDir.listFiles()) {
                if (hasExtension(file, ".jar")) {
                    files.add(file);
                }
            }
            return new DefaultClassPath(files);
        }
    }

    private static class ClasspathDistribution implements Distribution {
        public String getDisplayName() {
            return "Gradle classpath distribution";
        }

        public ClassPath getToolingImplementationClasspath(ProgressLoggerFactory progressLoggerFactory, File userHomeDir, BuildCancellationToken cancellationToken) {
            ClassPath classpath = new DefaultClassPath();
            DefaultModuleRegistry registry = new DefaultModuleRegistry(null);
            for (Module module : registry.getModule("gradle-launcher").getAllRequiredModules()) {
                classpath = classpath.plus(module.getClasspath());
            }
            return classpath;
        }
    }
}
