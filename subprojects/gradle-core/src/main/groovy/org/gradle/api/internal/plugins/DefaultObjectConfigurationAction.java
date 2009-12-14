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
package org.gradle.api.internal.plugins;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.plugins.ObjectConfigurationAction;
import org.gradle.configuration.ScriptObjectConfigurer;
import org.gradle.configuration.ScriptObjectConfigurerFactory;
import org.gradle.groovy.scripts.FileScriptSource;
import org.gradle.groovy.scripts.StrictScriptSource;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultObjectConfigurationAction implements ObjectConfigurationAction {
    private final FileResolver resolver;
    private final ScriptObjectConfigurerFactory configurerFactory;
    private final Set<Object> targets = new LinkedHashSet<Object>();
    private final Set<Object> scripts = new LinkedHashSet<Object>();
    private final Object[] defaultTargets;

    public DefaultObjectConfigurationAction(FileResolver resolver, ScriptObjectConfigurerFactory configurerFactory, Object... defaultTargets) {
        this.resolver = resolver;
        this.configurerFactory = configurerFactory;
        this.defaultTargets = defaultTargets;
    }

    public ObjectConfigurationAction to(Object... targets) {
        GUtil.flatten(targets, this.targets);
        return this;
    }

    public ObjectConfigurationAction script(Object script) {
        scripts.add(script);
        return this;
    }

    public void execute() {
        if (targets.isEmpty()) {
            to(defaultTargets);
        }
        
        for (Object script : scripts) {
            File scriptFile = resolver.resolve(script);
            ScriptObjectConfigurer configurer = configurerFactory.create(new StrictScriptSource(new FileScriptSource(
                    "script", scriptFile)));
            for (Object target : targets) {
                configurer.apply(target);
            }
        }
    }
}
