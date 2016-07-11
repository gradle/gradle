/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.configuration;

import org.gradle.api.Incubating;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.DependencyInjectingServiceLoader;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.service.ServiceRegistry;

/**
 * Selects a {@link ScriptPluginFactory} suitable for handling a given build script based
 * on its file name. Build script file names ending in ".gradle" are supported by the
 * {@link DefaultScriptPluginFactory}. Other files are delegated to the first available
 * implementation of the {@link ScriptPluginFactoryProvider} SPI to return non-null from
 * {@link ScriptPluginFactoryProvider#getFor(String)}. If all provider
 * implementations return null for a given file name, handling falls back to the
 * {@link DefaultScriptPluginFactory}. This approach allows users to name build scripts
 * with a suffix of choice, e.g. "build.groovy" or "my.build" instead of the typical
 * "build.gradle" while preserving default behaviour.
 *
 * @see ScriptPluginFactoryProvider
 * @since 2.14
 */
@Incubating
public class ScriptPluginFactorySelector implements ScriptPluginFactory {

    private final ScriptPluginFactory defaultScriptPluginFactory;
    private final ServiceRegistry serviceRegistry;

    public ScriptPluginFactorySelector(ScriptPluginFactory defaultScriptPluginFactory,
                                       ServiceRegistry serviceRegistry) {
        this.defaultScriptPluginFactory = defaultScriptPluginFactory;
        this.serviceRegistry = serviceRegistry;
    }

    @Override
    public ScriptPlugin create(ScriptSource scriptSource, ScriptHandler scriptHandler, ClassLoaderScope targetScope,
                               ClassLoaderScope baseScope, boolean topLevelScript) {
        return scriptPluginFactoryFor(scriptSource.getFileName())
            .create(scriptSource, scriptHandler, targetScope, baseScope, topLevelScript);
    }

    private ScriptPluginFactory scriptPluginFactoryFor(String fileName) {
        return fileName.endsWith(".gradle")
            ? defaultScriptPluginFactory
            : findScriptPluginFactoryFor(fileName);
    }

    private ScriptPluginFactory findScriptPluginFactoryFor(String fileName) {
        for (ScriptPluginFactoryProvider scriptPluginFactoryProvider : scriptPluginFactoryProviders()) {
            ScriptPluginFactory scriptPluginFactory = scriptPluginFactoryProvider.getFor(fileName);
            if (scriptPluginFactory != null) {
                return scriptPluginFactory;
            }
        }
        return defaultScriptPluginFactory;
    }

    private Iterable<ScriptPluginFactoryProvider> scriptPluginFactoryProviders() {
        return serviceLoader().load(ScriptPluginFactoryProvider.class, getClass().getClassLoader());
    }

    private DependencyInjectingServiceLoader serviceLoader() {
        return new DependencyInjectingServiceLoader(serviceRegistry);
    }
}
