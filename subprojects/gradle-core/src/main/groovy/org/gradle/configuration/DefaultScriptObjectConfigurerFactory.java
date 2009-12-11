/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.internal.artifacts.dsl.BuildScriptClasspathScriptTransformer;
import org.gradle.api.internal.artifacts.dsl.BuildScriptTransformer;
import org.gradle.api.internal.initialization.ScriptClassLoaderProvider;
import org.gradle.api.internal.project.ImportsReader;
import org.gradle.groovy.scripts.*;

public class DefaultScriptObjectConfigurerFactory implements ScriptObjectConfigurerFactory {
    private final ScriptCompilerFactory scriptCompilerFactory;
    private final ImportsReader importsReader;

    public DefaultScriptObjectConfigurerFactory(ScriptCompilerFactory scriptCompilerFactory,
                                                ImportsReader importsReader) {
        this.scriptCompilerFactory = scriptCompilerFactory;
        this.importsReader = importsReader;
    }

    public ScriptObjectConfigurer create(ScriptSource scriptSource) {
        return new ScriptObjectConfigurerImpl(scriptSource);
    }

    private class ScriptObjectConfigurerImpl implements ScriptObjectConfigurer {
        private final ScriptSource scriptSource;
        private ScriptClassLoaderProvider classLoaderProvider;
        private Class<? extends Script> scriptType = Script.class;
        private String classpathClosureName = "script";
        private Action<? super Script> initAction = new Action<Script>() {
            public void execute(Script script) {
            }
        };

        public ScriptObjectConfigurerImpl(ScriptSource scriptSource) {
            this.scriptSource = scriptSource;
        }

        public ScriptObjectConfigurer setClassLoaderProvider(ScriptClassLoaderProvider provider) {
            classLoaderProvider = provider;
            return this;
        }

        public ScriptObjectConfigurer setClasspathClosureName(String name) {
            this.classpathClosureName = name;
            return this;
        }

        public ScriptObjectConfigurer setScriptBaseClass(Class<? extends Script> baseClass) {
            scriptType = baseClass;
            return this;
        }

        public ScriptObjectConfigurer setInitAction(Action<? super Script> initAction) {
            this.initAction = initAction;
            return this;
        }

        public void apply(Object target) {
            ScriptSource withImports = new ImportsScriptSource(scriptSource, importsReader, null);
            ScriptCompiler compiler = scriptCompilerFactory.createCompiler(withImports);
            compiler.setClassloader(classLoaderProvider.getClassLoader());

            BuildScriptClasspathScriptTransformer classpathScriptTransformer
                    = new BuildScriptClasspathScriptTransformer(classpathClosureName);
            compiler.setTransformer(classpathScriptTransformer);
            ScriptRunner<? extends Script> classPathScript = compiler.compile(scriptType);
            classPathScript.setDelegate(target);

            classPathScript.run();
            classLoaderProvider.updateClassPath();

            compiler.setTransformer(new BuildScriptTransformer(classpathScriptTransformer));
            ScriptRunner<? extends Script> script = compiler.compile(scriptType);
            script.setDelegate(target);
            initAction.execute(script.getScript());
            script.run();
        }
    }
}