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

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Factory;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.invocation.BuildController;

import java.io.File;
import java.util.Collection;

public class BuildSrcUpdateFactory implements Factory<ClassPath> {
    private static final Logger LOGGER = Logging.getLogger(BuildSrcUpdateFactory.class);

    private final BuildController buildController;
    private final BuildSrcBuildListenerFactory listenerFactory;
    private final CachedClasspathTransformer cachedClasspathTransformer;

    public BuildSrcUpdateFactory(BuildController buildController, BuildSrcBuildListenerFactory listenerFactory, CachedClasspathTransformer cachedClasspathTransformer) {
        this.buildController = buildController;
        this.listenerFactory = listenerFactory;
        this.cachedClasspathTransformer = cachedClasspathTransformer;
    }

    @Override
    public ClassPath create() {
        Collection<File> classpath = build();
        LOGGER.debug("Gradle source classpath is: {}", classpath);
        return cachedClasspathTransformer.transform(DefaultClassPath.of(classpath));
    }

    private Collection<File> build() {
        BuildSrcBuildListenerFactory.Listener listener = listenerFactory.create();
        GradleInternal gradle = buildController.getGradle();
        gradle.addListener(listener);

        buildController.run();

        return listener.getRuntimeClasspath();
    }
}
