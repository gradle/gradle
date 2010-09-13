/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.testfixtures;

import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.*;
import org.gradle.api.internal.project.*;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.LoggingManager;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.cache.AutoCloseCacheFactory;
import org.gradle.cache.CacheFactory;
import org.gradle.cache.DefaultCacheFactory;
import org.gradle.initialization.ClassLoaderFactory;
import org.gradle.initialization.DefaultClassLoaderFactory;
import org.gradle.initialization.DefaultProjectDescriptor;
import org.gradle.initialization.DefaultProjectDescriptorRegistry;
import org.gradle.invocation.DefaultGradle;
import org.gradle.listener.DefaultListenerManager;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.logging.internal.DefaultProgressLoggerFactory;
import org.gradle.logging.internal.DefaultStyledTextOutputFactory;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.logging.internal.ProgressListener;
import org.gradle.util.GFileUtils;
import org.gradle.util.TrueTimeProvider;

import java.io.File;
import java.io.IOException;

/**
 * <p>Creates dummy instances of {@link org.gradle.api.Project} which you can use in testing custom task and plugin
 * implementations.</p>
 *
 * <p>To create a project instance:</p>
 *
 * <ol>
 *
 * <li>Create a {@code ProjectBuilder} instance by calling {@link #builder()}.</li>
 *
 * <li>Optionally, configure the builder.</li>
 *
 * <li>Call {@link #build()} to create the {@code Project} instance.</li>
 *
 * </ol>
 *
 * <p>You can reuse a builder to create multiple {@code Project} instances.</p>
 */
public class ProjectBuilder {
    private static final GlobalTestServices GLOBAL_SERVICES = new GlobalTestServices();
    private File projectDir;

    /**
     * Creates a project builder.
     *
     * @return The builder
     */
    public static ProjectBuilder builder() {
        return new ProjectBuilder();
    }

    /**
     * Specifies the project directory for the project to build.
     *
     * @param dir The project directory
     * @return A new ProjectBuilder.
     */
    public ProjectBuilder withProjectDir(File dir) {
        projectDir = GFileUtils.canonicalise(dir);
        return this;
    }

    /**
     * Creates the project.
     *
     * @return The project
     */
    public Project build() {
        if (projectDir == null) {
            try {
                projectDir = GFileUtils.canonicalise(File.createTempFile("gradle", "projectDir"));
                projectDir.delete();
                projectDir.mkdir();
                projectDir.deleteOnExit();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        final File homeDir = new File(projectDir, "gradleHome");

        StartParameter startParameter = new StartParameter();
        startParameter.setGradleUserHomeDir(new File(projectDir, "userHome"));

        ServiceRegistryFactory topLevelRegistry = new TestTopLevelBuildServiceRegistry(startParameter, homeDir);
        GradleInternal gradle = new DefaultGradle(null, startParameter, topLevelRegistry);

        DefaultProjectDescriptor projectDescriptor = new DefaultProjectDescriptor(null, "test", projectDir, new DefaultProjectDescriptorRegistry());
        ProjectInternal project = topLevelRegistry.get(IProjectFactory.class).createProject(projectDescriptor, null, gradle);

        gradle.setRootProject(project);
        gradle.setDefaultProject(project);

        return project;
    }

    private static class NoOpLoggingManager implements LoggingManagerInternal {
        private LogLevel stdoutLevel = LogLevel.LIFECYCLE;

        public LoggingManagerInternal captureStandardOutput(LogLevel level) {
            stdoutLevel = level;
            return this;
        }

        public LoggingManager disableStandardOutputCapture() {
            stdoutLevel = null;
            return this;
        }

        public boolean isStandardOutputCaptureEnabled() {
            return stdoutLevel != null;
        }

        public LogLevel getStandardOutputCaptureLevel() {
            return stdoutLevel;
        }

        public LoggingManagerInternal captureStandardError(LogLevel level) {
            return this;
        }

        public LoggingManagerInternal setLevel(LogLevel logLevel) {
            return this;
        }

        public LogLevel getStandardErrorCaptureLevel() {
            return LogLevel.ERROR;
        }

        public LoggingManagerInternal start() {
            return this;
        }

        public LoggingManagerInternal stop() {
            return this;
        }

        public void addStandardErrorListener(StandardOutputListener listener) {
        }

        public void addStandardOutputListener(StandardOutputListener listener) {
        }

        public void removeStandardOutputListener(StandardOutputListener listener) {
        }

        public void removeStandardErrorListener(StandardOutputListener listener) {
        }

        public void addOutputEventListener(OutputEventListener listener) {
        }

        public void removeOutputEventListener(OutputEventListener listener) {
        }

        public void colorStdOutAndStdErr(boolean colorOutput) {
        }
    }

    private static class GlobalTestServices extends DefaultServiceRegistry {
        protected ListenerManager createListenerManager() {
            return new DefaultListenerManager();
        }

        protected ClassPathRegistry createClassPathRegistry() {
            return new DefaultClassPathRegistry();
        }

        protected ClassLoaderFactory createClassLoaderFactory() {
            return new DefaultClassLoaderFactory(get(ClassPathRegistry.class));
        }

        protected CacheFactory createCacheFactory() {
            return new AutoCloseCacheFactory(new DefaultCacheFactory());
        }

        protected ProgressLoggerFactory createProgressLoggerFactory() {
            return new DefaultProgressLoggerFactory(get(ListenerManager.class).getBroadcaster(ProgressListener.class), new TrueTimeProvider());
        }

        protected Factory<LoggingManagerInternal> createLoggingManagerFactory() {
            return new Factory<LoggingManagerInternal>() {
                public LoggingManagerInternal create() {
                    return new NoOpLoggingManager();
                }
            };
        }

        protected StyledTextOutputFactory createStyledTextOutputFactory() {
            return new DefaultStyledTextOutputFactory(get(ListenerManager.class).getBroadcaster(OutputEventListener.class), new TrueTimeProvider());
        }
        
        protected IsolatedAntBuilder createIsolatedAntBuilder() {
            return new DefaultIsolatedAntBuilder(get(ClassPathRegistry.class));
        }
    }

    private static class TestTopLevelBuildServiceRegistry extends TopLevelBuildServiceRegistry {
        private final File homeDir;

        public TestTopLevelBuildServiceRegistry(StartParameter startParameter, File homeDir) {
            super(ProjectBuilder.GLOBAL_SERVICES, startParameter);
            this.homeDir = homeDir;
        }

        protected GradleDistributionLocator createGradleDistributionLocator() {
            return new GradleDistributionLocator() {
                public File getGradleHome() {
                    return homeDir;
                }
            };
        }
    }
}
