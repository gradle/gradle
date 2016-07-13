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

package org.gradle.ide.cdt.tasks;

import groovy.lang.Closure;
import org.gradle.api.Incubating;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.tasks.Internal;
import org.gradle.ide.cdt.model.CprojectSettings;
import org.gradle.internal.Factory;
import org.gradle.listener.ActionBroadcast;
import org.gradle.plugins.ide.api.GeneratorTask;
import org.gradle.plugins.ide.internal.generator.generator.PersistableConfigurationObject;
import org.gradle.plugins.ide.internal.generator.generator.PersistableConfigurationObjectGenerator;

/**
 * Task for generating the metadata file.
 *
 * @param <T> The domain object for the metadata file.
 */
@Incubating
public class GenerateMetadataFileTask<T extends PersistableConfigurationObject> extends GeneratorTask<T> {

    @Internal
    private Factory<T> factory;

    @Internal
    private ActionBroadcast<T> configures = new ActionBroadcast<T>();

    @Internal
    private CprojectSettings settings;

    public GenerateMetadataFileTask() {
        generator = new PersistableConfigurationObjectGenerator<T>() {
            public T create() {
                return GenerateMetadataFileTask.this.getFactory().create();
            }

            public void configure(T object) {
                GenerateMetadataFileTask.this.getConfigures().execute(object);
            }
        };
    }

    public void factory(Closure factory) {
        this.factory = (Factory) factory;
    }

    public void onConfigure(Closure configure) {
        configures.add(new ClosureBackedAction(configure));
    }

    public Factory<T> getFactory() {
        return factory;
    }

    public ActionBroadcast<T> getConfigures() {
        return configures;
    }

    public CprojectSettings getSettings() {
        return settings;
    }

    public void setSettings(CprojectSettings settings) {
        this.settings = settings;
    }
}
