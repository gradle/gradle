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

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.PersistentCache;
import org.gradle.internal.Factory;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.invocation.BuildController;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class BuildSrcUpdateFactory implements Factory<DefaultClassPath> {
    private final PersistentCache cache;
    private final BuildController buildController;
    private BuildSrcBuildListenerFactory listenerFactory;
    private static final Logger LOGGER = Logging.getLogger(BuildSrcUpdateFactory.class);

    public BuildSrcUpdateFactory(PersistentCache cache, BuildController buildController, BuildSrcBuildListenerFactory listenerFactory) {
        this.cache = cache;
        this.buildController = buildController;
        this.listenerFactory = listenerFactory;
    }

    public DefaultClassPath create() {
        File markerFile = new File(cache.getBaseDir(), "built.bin");
        final boolean rebuild = !markerFile.exists();

        Collection<File> classpath = build(rebuild);
        LOGGER.debug("Gradle source classpath is: {}", classpath);
        try {
            markerFile.createNewFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new DefaultClassPath(classpath);
    }

    private Collection<File> build(boolean rebuild) {
        BuildSrcBuildListenerFactory.Listener listener = listenerFactory.create(rebuild);
        GradleInternal gradle = buildController.getGradle();
        gradle.addListener(listener);

        buildController.run();

        return listener.getRuntimeClasspath();
    }
}
