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

import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.internal.ScriptSourceListener;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.scripts.ScriptingLanguages;
import org.gradle.scripts.ScriptingLanguage;

import java.util.List;

/**
 * Selects a {@link ScriptPluginFactory} suitable for handling a given build script based
 * on its file name. Build script file names ending in ".gradle" are supported by the
 * {@link DefaultScriptPluginFactory}. Other files are delegated to the first available
 * matching implementation of the {@link ScriptingLanguage} SPI. If no provider
 * implementations matches for a given file name, handling falls back to the
 * {@link DefaultScriptPluginFactory}. This approach allows users to name build scripts
 * with a suffix of choice, e.g. "build.groovy" or "my.build" instead of the typical
 * "build.gradle" while preserving default behaviour which is to fallback to Groovy support.
 *
 * This factory wraps each {@link ScriptPlugin} implementation in a {@link BuildOperationScriptPlugin}.
 *
 * @since 2.14
 */
public class ScriptPluginFactorySelector implements ScriptPluginFactory {

    /**
     * Scripting language ScriptPluginFactory instantiator.
     *
     * @since 4.0
     */
    public interface ProviderInstantiator {
        ScriptPluginFactory instantiate(String providerClassName);
    }

    /**
     * Default scripting language ScriptPluginFactory instantiator.
     *
     * @param instantiator the instantiator
     * @return the provider instantiator
     * @since 4.0
     */
    public static ProviderInstantiator defaultProviderInstantiatorFor(final Instantiator instantiator) {
        return new ProviderInstantiator() {

            @Override
            public ScriptPluginFactory instantiate(String providerClassName) {
                Class<?> providerClass = loadProviderClass(providerClassName);
                return (ScriptPluginFactory) instantiator.newInstance(providerClass);
            }

            private Class<?> loadProviderClass(String providerClassName) {
                try {
                    return getClass().getClassLoader().loadClass(providerClassName);
                } catch (ClassNotFoundException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        };
    }

    private final ScriptPluginFactory defaultScriptPluginFactory;
    private final ProviderInstantiator providerInstantiator;
    private final BuildOperationExecutor buildOperationExecutor;
    private final UserCodeApplicationContext userCodeApplicationContext;
    private final ScriptSourceListener scriptSourceListener;

    public ScriptPluginFactorySelector(
        ScriptPluginFactory defaultScriptPluginFactory,
        ProviderInstantiator providerInstantiator,
        BuildOperationExecutor buildOperationExecutor,
        UserCodeApplicationContext userCodeApplicationContext,
        ScriptSourceListener scriptSourceListener
    ) {
        this.defaultScriptPluginFactory = defaultScriptPluginFactory;
        this.providerInstantiator = providerInstantiator;
        this.buildOperationExecutor = buildOperationExecutor;
        this.userCodeApplicationContext = userCodeApplicationContext;
        this.scriptSourceListener = scriptSourceListener;
    }

    @Override
    public ScriptPlugin create(
        ScriptSource scriptSource, ScriptHandler scriptHandler, ClassLoaderScope targetScope,
        ClassLoaderScope baseScope, boolean topLevelScript
    ) {
        scriptSourceListener.scriptSourceObserved(scriptSource);
        ScriptPlugin scriptPlugin = scriptPluginFactoryFor(scriptSource.getFileName())
            .create(scriptSource, scriptHandler, targetScope, baseScope, topLevelScript);
        return new BuildOperationScriptPlugin(scriptPlugin, buildOperationExecutor, userCodeApplicationContext);
    }

    private ScriptPluginFactory scriptPluginFactoryFor(String fileName) {
        for (ScriptingLanguage scriptingLanguage : scriptingLanguages()) {
            if (fileName.endsWith(scriptingLanguage.getExtension())) {
                String provider = scriptingLanguage.getProvider();
                if (provider != null) {
                    return instantiate(provider);
                }
                return defaultScriptPluginFactory;
            }
        }
        return defaultScriptPluginFactory;
    }

    private List<ScriptingLanguage> scriptingLanguages() {
        return ScriptingLanguages.all();
    }

    private ScriptPluginFactory instantiate(String provider) {
        return providerInstantiator.instantiate(provider);
    }
}
