/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.plugin.use.resolve.internal;

import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.configuration.ScriptPlugin;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.configuration.ScriptPluginFactorySelector;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.StringScriptSource;


/**
 * Script plugin loader used by {@link ScriptPluginPluginResolver}.
 */
public class ScriptPluginPluginLoader {

    public ScriptPlugin load(String scriptContent,
                             String scriptIdentifier,
                             String scriptDisplayName,
                             ProjectRegistry<ProjectInternal> projectRegistry,
                             ScriptHandler scriptHandler,
                             ScriptPluginFactory scriptPluginFactory) {

        ClassLoaderScope buildSrcScope = projectRegistry.getRootProject().getClassLoaderScope().getParent();
        ClassLoaderScope scriptPluginScope = buildSrcScope.createChild("script-plugin-" + scriptIdentifier);

        ScriptSource scriptSource = new StringScriptSource(scriptDisplayName, scriptContent);
        return ((ScriptPluginFactorySelector) scriptPluginFactory).createRaw(scriptSource, scriptHandler, scriptPluginScope, buildSrcScope, false);
    }
}
