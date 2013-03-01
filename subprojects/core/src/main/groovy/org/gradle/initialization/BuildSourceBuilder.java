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

package org.gradle.initialization;

import org.gradle.BuildAdapter;
import org.gradle.GradleLauncher;
import org.gradle.StartParameter;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.plugins.EmbeddableJavaProject;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.internal.Factory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class BuildSourceBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildSourceBuilder.class);

    private final GradleLauncherFactory gradleLauncherFactory;
    private final ClassLoaderRegistry classLoaderRegistry;
    private final CacheRepository cacheRepository;

    private static final String DEFAULT_BUILD_SOURCE_SCRIPT_RESOURCE = "defaultBuildSourceScript.txt";

    public BuildSourceBuilder(GradleLauncherFactory gradleLauncherFactory, ClassLoaderRegistry classLoaderRegistry, CacheRepository cacheRepository) {
        this.gradleLauncherFactory = gradleLauncherFactory;
        this.classLoaderRegistry = classLoaderRegistry;
        this.cacheRepository = cacheRepository;
    }

    public URLClassLoader buildAndCreateClassLoader(StartParameter startParameter) {
        ClassPath classpath = createBuildSourceClasspath(startParameter);
        return new URLClassLoader(classpath.getAsURLArray(), classLoaderRegistry.getRootClassLoader());
    }

    private ClassPath createBuildSourceClasspath(StartParameter startParameter) {
        assert startParameter.getCurrentDir() != null && startParameter.getBuildFile() == null;

        LOGGER.debug("Starting to build the build sources.");
        if (!startParameter.getCurrentDir().isDirectory()) {
            LOGGER.debug("Gradle source dir does not exist. We leave.");
            return new DefaultClassPath();
        }
        LOGGER.info("================================================" + " Start building buildSrc");

        // If we were not the most recent version of Gradle to build the buildSrc dir, then do a clean build
        // Otherwise, just to a regular build
        final PersistentCache buildSrcCache = cacheRepository.
                cache("buildSrc").
                withLockMode(FileLockManager.LockMode.None).
                forObject(startParameter.getCurrentDir()).
                withVersionStrategy(CacheBuilder.VersionStrategy.SharedCacheInvalidateOnVersionChange).
                open();

        GradleLauncher gradleLauncher = buildGradleLauncher(startParameter);
        return buildSrcCache.useCache("rebuild buildSrc", new BuildSrcUpdateFactory(buildSrcCache, gradleLauncher));
    }

    private GradleLauncher buildGradleLauncher(StartParameter startParameter) {
        final StartParameter startParameterArg = startParameter.newInstance();
        startParameterArg.setProjectProperties(startParameter.getProjectProperties());
        startParameterArg.setSearchUpwards(false);
        startParameterArg.setProfile(startParameter.isProfile());
        return gradleLauncherFactory.newInstance(startParameterArg);
    }

    static URL getDefaultScript() {
        return BuildSourceBuilder.class.getResource(DEFAULT_BUILD_SOURCE_SCRIPT_RESOURCE);
    }

    private static class BuildSrcBuildListener extends BuildAdapter implements ModelConfigurationListener {
        private EmbeddableJavaProject projectInfo;
        private Set<File> classpath;
        private final boolean rebuild;

        public BuildSrcBuildListener(boolean rebuild) {
            this.rebuild = rebuild;
        }

        @Override
        public void projectsLoaded(Gradle gradle) {
            gradle.getRootProject().apply(WrapUtil.toMap("from", getDefaultScript()));
        }

        public Collection<File> getRuntimeClasspath() {
            return classpath;
        }

        public void onConfigure(GradleInternal gradle) {
            projectInfo = gradle.getRootProject().getConvention().getPlugin(EmbeddableJavaProject.class);
            gradle.getStartParameter().setTaskNames(rebuild ? projectInfo.getRebuildTasks() : projectInfo.getBuildTasks());
            classpath = projectInfo.getRuntimeClasspath().getFiles();
        }
    }

    private static class BuildSrcUpdateFactory implements Factory<DefaultClassPath> {
        private final PersistentCache cache;
        private final GradleLauncher gradleLauncher;

        public BuildSrcUpdateFactory(PersistentCache cache, GradleLauncher gradleLauncher) {
            this.cache = cache;
            this.gradleLauncher = gradleLauncher;
        }

        public DefaultClassPath create() {
            File markerFile = new File(cache.getBaseDir(), "built.bin");
            final boolean rebuild = !markerFile.exists();

            BuildSrcBuildListener listener = new BuildSrcBuildListener(rebuild);
            gradleLauncher.addListener(listener);
            gradleLauncher.run().rethrowFailure();

            Collection<File> classpath = listener.getRuntimeClasspath();
            LOGGER.debug("Gradle source classpath is: {}", classpath);
            LOGGER.info("================================================" + " Finished building buildSrc");
            try {
                markerFile.createNewFile();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return new DefaultClassPath(classpath);
        }
    }
}
