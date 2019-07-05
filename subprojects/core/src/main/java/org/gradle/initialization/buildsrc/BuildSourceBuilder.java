/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.initialization.buildsrc;

import org.gradle.StartParameter;
import org.gradle.api.Transformer;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.LockOptions;
import org.gradle.initialization.DefaultSettings;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.PublicBuildPath;
import org.gradle.internal.build.StandAloneNestedBuild;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class BuildSourceBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildSourceBuilder.class);
    private static final BuildBuildSrcBuildOperationType.Result BUILD_BUILDSRC_RESULT = new BuildBuildSrcBuildOperationType.Result() {
    };
    public static final String BUILD_SRC = "buildSrc";

    private final BuildState currentBuild;
    private final ClassLoaderScope classLoaderScope;
    private final FileLockManager fileLockManager;
    private final BuildOperationExecutor buildOperationExecutor;
    private final CachedClasspathTransformer cachedClasspathTransformer;
    private final BuildSrcBuildListenerFactory buildSrcBuildListenerFactory;
    private final BuildStateRegistry buildRegistry;
    private final PublicBuildPath publicBuildPath;

    public BuildSourceBuilder(BuildState currentBuild, ClassLoaderScope classLoaderScope, FileLockManager fileLockManager, BuildOperationExecutor buildOperationExecutor, CachedClasspathTransformer cachedClasspathTransformer, BuildSrcBuildListenerFactory buildSrcBuildListenerFactory, BuildStateRegistry buildRegistry, PublicBuildPath publicBuildPath) {
        this.currentBuild = currentBuild;
        this.classLoaderScope = classLoaderScope;
        this.fileLockManager = fileLockManager;
        this.buildOperationExecutor = buildOperationExecutor;
        this.cachedClasspathTransformer = cachedClasspathTransformer;
        this.buildSrcBuildListenerFactory = buildSrcBuildListenerFactory;
        this.buildRegistry = buildRegistry;
        this.publicBuildPath = publicBuildPath;
    }

    public ClassLoaderScope buildAndCreateClassLoader(File rootDir, StartParameter containingBuildParameters) {
        File buildSrcDir = new File(rootDir, DefaultSettings.DEFAULT_BUILD_SRC_DIR);
        ClassPath classpath = createBuildSourceClasspath(buildSrcDir, containingBuildParameters);
        return classLoaderScope.createChild(buildSrcDir.getAbsolutePath())
            .export(classpath)
            .lock();
    }

    private ClassPath createBuildSourceClasspath(File buildSrcDir, final StartParameter containingBuildParameters) {
        if (!buildSrcDir.isDirectory()) {
            LOGGER.debug("Gradle source dir does not exist. We leave.");
            return ClassPath.EMPTY;
        }

        final StartParameter buildSrcStartParameter = containingBuildParameters.newBuild();
        buildSrcStartParameter.setCurrentDir(buildSrcDir);
        buildSrcStartParameter.setProjectProperties(containingBuildParameters.getProjectProperties());
        buildSrcStartParameter.setSearchUpwards(false);
        buildSrcStartParameter.setProfile(containingBuildParameters.isProfile());
        final BuildDefinition buildDefinition = BuildDefinition.fromStartParameterForBuild(buildSrcStartParameter, "buildSrc", buildSrcDir, publicBuildPath);
        assert buildSrcStartParameter.getBuildFile() == null;

        return buildOperationExecutor.call(new CallableBuildOperation<ClassPath>() {
            @Override
            public ClassPath call(BuildOperationContext context) {
                ClassPath classPath = buildBuildSrc(buildDefinition);
                context.setResult(BUILD_BUILDSRC_RESULT);
                return classPath;
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Build buildSrc").
                    progressDisplayName("Building buildSrc").
                    details(new BuildBuildSrcBuildOperationType.Details() {

                        @Override
                        public String getBuildPath() {
                            return publicBuildPath.getBuildPath().toString();
                        }
                    });
            }
        });
    }

    private ClassPath buildBuildSrc(final BuildDefinition buildDefinition) {
        StandAloneNestedBuild nestedBuild = buildRegistry.addNestedBuild(buildDefinition, currentBuild);
        return nestedBuild.run(new Transformer<ClassPath, BuildController>() {
            @Override
            public ClassPath transform(BuildController buildController) {
                File lockTarget = new File(buildDefinition.getBuildRootDir(), ".gradle/noVersion/buildSrc");
                FileLock lock = fileLockManager.lock(lockTarget, LOCK_OPTIONS, "buildSrc build lock");
                try {
                    return new BuildSrcUpdateFactory(buildController, buildSrcBuildListenerFactory, cachedClasspathTransformer).create();
                } finally {
                    lock.close();
                }
            }
        });
    }

    private static final LockOptions LOCK_OPTIONS = mode(FileLockManager.LockMode.Exclusive).useCrossVersionImplementation();
}
