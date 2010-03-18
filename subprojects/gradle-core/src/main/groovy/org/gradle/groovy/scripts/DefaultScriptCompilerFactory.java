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
package org.gradle.groovy.scripts;

import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.util.HashUtil;
import org.gradle.util.ReflectionUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultScriptCompilerFactory implements ScriptCompilerFactory {
    private final ScriptCompilationHandler scriptCompilationHandler;
    private final CacheRepository cacheRepository;
    private final ScriptRunnerFactory scriptRunnerFactory;

    public DefaultScriptCompilerFactory(ScriptCompilationHandler scriptCompilationHandler,
                                        ScriptRunnerFactory scriptRunnerFactory, CacheRepository cacheRepository) {
        this.scriptCompilationHandler = scriptCompilationHandler;
        this.cacheRepository = cacheRepository;
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
            this.source = new CachingScriptSource(source);
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

            T script = loadViaCache(classloader, scriptType);
            script.setScriptSource(source);
            script.setContextClassloader(classloader);
            return scriptRunnerFactory.create(script);
        }

        private <T extends Script> T loadViaCache(ClassLoader classLoader, Class<T> scriptBaseClass) {
            Map<String, Object> properties = new HashMap<String, Object>();
            properties.put("source.filename", source.getFileName());
            properties.put("source.hash", HashUtil.createHash(source.getResource().getText()));

            PersistentCache cache = cacheRepository.cache(String.format("scripts/%s", source.getClassName())).withProperties(properties).open();
            File classesDir;
            if (transformer != null) {
                String subdirName = String.format("%s_%s", transformer.getId(), scriptBaseClass.getSimpleName());
                classesDir = new File(cache.getBaseDir(), subdirName);
            } else {
                classesDir = new File(cache.getBaseDir(), scriptBaseClass.getSimpleName());
            }

            if (!cache.isValid() || !classesDir.exists()) {
                scriptCompilationHandler.compileToDir(source, classLoader, classesDir, transformer, scriptBaseClass);
                cache.markValid();
            }
            Class<? extends T> scriptClass = scriptCompilationHandler.loadFromDir(source, classLoader, classesDir,
                    scriptBaseClass);
            return scriptBaseClass.cast(ReflectionUtil.newInstance(scriptClass, new Object[0]));
        }
    }
}
