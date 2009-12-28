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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.plugins.ObjectConfigurationAction;
import org.gradle.configuration.ScriptPlugin;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.groovy.scripts.FileScriptSource;
import org.gradle.groovy.scripts.StrictScriptSource;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultObjectConfigurationAction implements ObjectConfigurationAction {
    private final FileResolver resolver;
    private final ScriptPluginFactory configurerFactory;
    private final Set<Object> targets = new LinkedHashSet<Object>();
    private final Set<Runnable> actions = new LinkedHashSet<Runnable>();
    private final Object[] defaultTargets;

    public DefaultObjectConfigurationAction(FileResolver resolver, ScriptPluginFactory configurerFactory,
                                            Object... defaultTargets) {
        this.resolver = resolver;
        this.configurerFactory = configurerFactory;
        this.defaultTargets = defaultTargets;
    }

    public ObjectConfigurationAction to(Object... targets) {
        GUtil.flatten(targets, this.targets);
        return this;
    }

    public ObjectConfigurationAction script(final Object script) {
        actions.add(new Runnable() {
            public void run() {
                applyScript(script);
            }
        });
        return this;
    }

    public ObjectConfigurationAction plugin(final Class<? extends Plugin> pluginClass) {
        actions.add(new Runnable() {
            public void run() {
                applyPlugin(pluginClass);
            }
        });
        return this;
    }

    public ObjectConfigurationAction plugin(final String pluginName) {
        actions.add(new Runnable() {
            public void run() {
                applyPlugin(pluginName);
            }
        });
        return this;
    }

    private void applyScript(Object script) {
        File scriptFile = resolver.resolve(script);
        ScriptPlugin configurer = configurerFactory.create(new StrictScriptSource(new FileScriptSource(
                "script", scriptFile)));
        for (Object target : targets) {
            configurer.use(target);
        }
    }

    private void applyPlugin(Class<? extends Plugin> pluginClass) {
        for (Object target : targets) {
            if (target instanceof Project) {
                Project project = (Project) target;
                project.usePlugin(pluginClass);
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }

    private void applyPlugin(String pluginName) {
        for (Object target : targets) {
            if (target instanceof Project) {
                Project project = (Project) target;
                project.usePlugin(pluginName);
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }

    public void execute() {
        if (targets.isEmpty()) {
            to(defaultTargets);
        }

        for (Runnable action : actions) {
            action.run();
        }
    }
}
