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
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.LockOptions;
import org.gradle.initialization.DefaultSettings;
import org.gradle.initialization.NestedBuildFactory;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.NestedBuildState;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.plugin.management.internal.DefaultPluginRequests;
import org.gradle.util.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class BuildSourceBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildSourceBuilder.class);
    private static final BuildBuildSrcBuildOperationType.Result BUILD_BUILDSRC_RESULT = new BuildBuildSrcBuildOperationType.Result() {
    };
    public static final String BUILD_SRC = "buildSrc";

    private final NestedBuildFactory nestedBuildFactory;
    private final ClassLoaderScope classLoaderScope;
    private final FileLockManager fileLockManager;
    private final BuildOperationExecutor buildOperationExecutor;
    private final CachedClasspathTransformer cachedClasspathTransformer;
    private final BuildSrcBuildListenerFactory buildSrcBuildListenerFactory;
    private final BuildStateRegistry buildRegistry;

    public BuildSourceBuilder(NestedBuildFactory nestedBuildFactory, ClassLoaderScope classLoaderScope, FileLockManager fileLockManager, BuildOperationExecutor buildOperationExecutor, CachedClasspathTransformer cachedClasspathTransformer, BuildSrcBuildListenerFactory buildSrcBuildListenerFactory, BuildStateRegistry buildRegistry) {
        this.nestedBuildFactory = nestedBuildFactory;
        this.classLoaderScope = classLoaderScope;
        this.fileLockManager = fileLockManager;
        this.buildOperationExecutor = buildOperationExecutor;
        this.cachedClasspathTransformer = cachedClasspathTransformer;
        this.buildSrcBuildListenerFactory = buildSrcBuildListenerFactory;
        this.buildRegistry = buildRegistry;
    }

    public ClassLoaderScope buildAndCreateClassLoader(GradleInternal gradle, File rootDir, StartParameter containingBuildParameters) {
        File buildSrcDir = new File(rootDir, DefaultSettings.DEFAULT_BUILD_SRC_DIR);
        ClassPath classpath = createBuildSourceClasspath(gradle, buildSrcDir, containingBuildParameters);
        return classLoaderScope.createChild(buildSrcDir.getAbsolutePath())
            .export(classpath)
            .lock();
    }

    private ClassPath createBuildSourceClasspath(final GradleInternal gradle, File buildSrcDir, final StartParameter containingBuildParameters) {
        if (!buildSrcDir.isDirectory()) {
            LOGGER.debug("Gradle source dir does not exist. We leave.");
            return ClassPath.EMPTY;
        }

        final StartParameter buildSrcStartParameter = containingBuildParameters.newBuild();
        buildSrcStartParameter.setCurrentDir(buildSrcDir);
        buildSrcStartParameter.setProjectProperties(containingBuildParameters.getProjectProperties());
        buildSrcStartParameter.setSearchUpwards(false);
        buildSrcStartParameter.setProfile(containingBuildParameters.isProfile());
        final BuildDefinition buildDefinition = BuildDefinition.fromStartParameterForBuild(buildSrcStartParameter, buildSrcDir, DefaultPluginRequests.EMPTY);
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
                    details(new BuildBuildSrcBuildOperationType.Details(){

                        @Override
                        public String getBuildPath() {
                            return gradle.getIdentityPath().toString();
                        }
                    });
            }
        });
    }

    private ClassPath buildBuildSrc(final BuildDefinition buildDefinition) {
        NestedBuildState nestedBuild = buildRegistry.addNestedBuild(buildDefinition, nestedBuildFactory);
        return nestedBuild.run(new Transformer<ClassPath, BuildController>() {
            @Override
            public ClassPath transform(BuildController buildController) {
                GradleInternal build = buildController.getGradle();
                StartParameter startParameter = buildController.getGradle().getStartParameter();
                if (build.getParent().findIdentityPath() == null) {
                    // When nested inside a nested build, we need to synthesize a path for this build, as the root project is not yet known for the parent build
                    // Use the directory structure to do this. This means that the buildSrc build and its containing build may end up with different paths
                    Path path = build.getParent().getParent().getIdentityPath().child(startParameter.getCurrentDir().getParentFile().getName()).child(startParameter.getCurrentDir().getName());
                    build.setIdentityPath(path);
                }
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
