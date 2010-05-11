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
package org.gradle.api.test;

import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.internal.project.DefaultServiceRegistry;
import org.gradle.api.internal.project.TopLevelBuildServiceRegistry;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.LoggingManager;
import org.gradle.api.logging.StandardOutputCapture;
import org.gradle.cache.AutoCloseCacheFactory;
import org.gradle.cache.CacheFactory;
import org.gradle.cache.DefaultCacheFactory;
import org.gradle.groovy.scripts.StringScriptSource;
import org.gradle.initialization.ClassLoaderFactory;
import org.gradle.initialization.DefaultClassLoaderFactory;
import org.gradle.invocation.DefaultGradle;
import org.gradle.listener.DefaultListenerManager;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.DefaultProgressLoggerFactory;
import org.gradle.logging.LoggingManagerFactory;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.util.GFileUtils;

import java.io.File;

/**
 * Creates dummy instances of {@link org.gradle.api.Project} which you can use for testing custom task and plugin
 * implementations.
 */
public class ProjectBuilder {
    private static final GlobalTestServices GLOBAL_SERVICES = new GlobalTestServices();
    private File projectDir;

    private ProjectBuilder(File projectDir) {
        this.projectDir = GFileUtils.canonicalise(projectDir);
    }

    /**
     * Specifies the project directory for the project to build.
     *
     * @param dir The project directory
     * @return A new ProjectBuilder.
     */
    public static ProjectBuilder withProjectDir(File dir) {
        return new ProjectBuilder(dir);
    }

    /**
     * Creates the project.
     *
     * @return The project
     */
    public Project create() {
        StartParameter startParameter = new StartParameter();
        startParameter.setGradleHomeDir(new File(projectDir, "gradleHome"));
        startParameter.setGradleUserHomeDir(new File(projectDir, "userHome"));
        TopLevelBuildServiceRegistry topLevelRegistry = new TopLevelBuildServiceRegistry(GLOBAL_SERVICES,
                startParameter);
        GradleInternal gradle = new DefaultGradle(null, startParameter, topLevelRegistry);
        return new DefaultProject("test", null, projectDir, new StringScriptSource("Empty build file", ""), gradle,
                gradle.getServiceRegistryFactory());
    }

    private static class NoOpLoggingManager implements LoggingManager {
        public LoggingManager captureStandardOutput(LogLevel level) {
            return this;
        }

        public LoggingManager disableStandardOutputCapture() {
            return this;
        }

        public boolean isStandardOutputCaptureEnabled() {
            return false;
        }

        public LogLevel getStandardOutputCaptureLevel() {
            return null;
        }

        public LoggingManager setLevel(LogLevel logLevel) {
            return this;
        }

        public StandardOutputCapture start() {
            return this;
        }

        public StandardOutputCapture stop() {
            return this;
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
            return new DefaultProgressLoggerFactory(get(ListenerManager.class));
        }

        protected LoggingManagerFactory createLoggingManagerFactory() {
            return new LoggingManagerFactory() {
                public LoggingManager create() {
                    return new NoOpLoggingManager();
                }
            };
        }
    }
}
