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

import org.gradle.api.internal.artifacts.dsl.InitScriptClasspathScriptTransformer;
import org.gradle.api.internal.artifacts.dsl.InitScriptTransformer;
import org.gradle.api.internal.GradleInternal;
import org.gradle.groovy.scripts.*;
import groovy.lang.Script;

/**
 * Processes (and runs) an init script for a specified build.  Handles defining
 * the classpath based on the initscript {} configuration closure.
 */
public class DefaultInitScriptProcessor implements InitScriptProcessor {
    private final ScriptProcessorFactory scriptProcessorFactory;
    private final InitScriptMetaData initScriptMetaData;

    public DefaultInitScriptProcessor(ScriptProcessorFactory scriptProcessorFactory,
                              InitScriptMetaData initScriptMetaData) {
        this.scriptProcessorFactory = scriptProcessorFactory;
        this.initScriptMetaData = initScriptMetaData;
    }

    public void process(ScriptSource initScript, GradleInternal gradle) {
        ScriptProcessor processor = scriptProcessorFactory.createProcessor(initScript);
        processor.setClassloader(gradle.getClassLoaderProvider().getClassLoader());

        processor.setTransformer(new InitScriptClasspathScriptTransformer());
        Script classPathScript = processor.process(ScriptWithSource.class);
        initScriptMetaData.applyMetaData(classPathScript, gradle);

        classPathScript.run();
        gradle.getClassLoaderProvider().updateClassPath();

        processor.setTransformer(new InitScriptTransformer());
        Script buildScript = processor.process(ScriptWithSource.class);
        initScriptMetaData.applyMetaData(buildScript, gradle);

        buildScript.run();
    }
}