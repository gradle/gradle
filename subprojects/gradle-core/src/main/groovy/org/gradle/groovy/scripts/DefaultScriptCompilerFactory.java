/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.groovy.scripts;

import org.gradle.CacheUsage;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class DefaultScriptCompilerFactory implements ScriptCompilerFactory {
    private final ScriptCompilationHandler scriptCompilationHandler;
    private final CacheUsage cacheUsage;
    private final File cacheDir;
    private final ScriptRunnerFactory scriptRunnerFactory;

    public DefaultScriptCompilerFactory(ScriptCompilationHandler scriptCompilationHandler, CacheUsage cacheUsage,
                                        File userHomeDir, ScriptRunnerFactory scriptRunnerFactory) {
        this.scriptCompilationHandler = scriptCompilationHandler;
        this.cacheUsage = cacheUsage;
        this.cacheDir = new File(userHomeDir, "scriptCache");
        this.scriptRunnerFactory = scriptRunnerFactory;
    }

    public ScriptCompiler createCompiler(ScriptSource source) {
        return new ScriptCompilerImpl(source);
    }

    private class ScriptCompilerImpl implements ScriptCompiler {
        private final ScriptSource source;
        private ClassLoader classloader;
        private Transformer transformer;

        public ScriptCompilerImpl(ScriptSource source) {
            this.source = source;
        }

        public ScriptCompiler setClassloader(ClassLoader classloader) {
            this.classloader = classloader;
            return this;
        }

        public ScriptCompiler setTransformer(Transformer transformer) {
            this.transformer = transformer;
            return this;
        }

        public <T extends Script> ScriptRunner<T> compile(Class<T> scriptType) {
            ClassLoader classloader = this.classloader != null ? this.classloader
                    : Thread.currentThread().getContextClassLoader();

            T script;
            if (cacheUsage != CacheUsage.OFF) {
                script = loadViaCache(classloader, scriptType);
            } else {
                script = loadWithoutCache(classloader, scriptType);
            }
            script.setScriptSource(source);
            return scriptRunnerFactory.create(script);
        }

        private <T extends Script> T loadWithoutCache(ClassLoader classLoader, Class<T> scriptBaseClass) {
            return scriptCompilationHandler.createScriptOnTheFly(source, classLoader, transformer, scriptBaseClass);
        }

        private <T extends Script> T loadViaCache(ClassLoader classLoader, Class<T> scriptBaseClass) {
            File scriptCacheDir = new File(cacheDir, source.getClassName());
            if (transformer != null) {
                scriptCacheDir = new File(scriptCacheDir, transformer.getClass().getSimpleName());
            } else {
                scriptCacheDir = new File(scriptCacheDir, "NoTransformer");
            }

            if (cacheUsage == CacheUsage.ON) {
                T cachedScript = scriptCompilationHandler.loadFromCache(source, classLoader, scriptCacheDir,
                        scriptBaseClass);
                if (cachedScript != null) {
                    return cachedScript;
                }
            }
            scriptCompilationHandler.writeToCache(source, classLoader, scriptCacheDir, transformer, scriptBaseClass);
            return scriptCompilationHandler.loadFromCache(source, classLoader, scriptCacheDir, scriptBaseClass);
        }
    }
}
