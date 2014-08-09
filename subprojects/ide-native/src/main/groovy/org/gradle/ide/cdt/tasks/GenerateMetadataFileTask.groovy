/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.ide.cdt.tasks

import org.gradle.api.Incubating
import org.gradle.api.internal.ClosureBackedAction
import org.gradle.internal.Factory
import org.gradle.listener.ActionBroadcast
import org.gradle.ide.cdt.model.CprojectSettings
import org.gradle.plugins.ide.api.GeneratorTask
import org.gradle.plugins.ide.internal.generator.generator.PersistableConfigurationObject
import org.gradle.plugins.ide.internal.generator.generator.PersistableConfigurationObjectGenerator

@Incubating
class GenerateMetadataFileTask<T extends PersistableConfigurationObject> extends GeneratorTask<T> {

    Factory<T> factory
    ActionBroadcast<T> configures = new ActionBroadcast<T>()
    CprojectSettings settings

    GenerateMetadataFileTask() {
        generator = new PersistableConfigurationObjectGenerator() {
            public create() {
                GenerateMetadataFileTask.this.factory.create();
            }

            public void configure(object) {
                GenerateMetadataFileTask.this.configures.execute(object);
            }
        }
    }

    void factory(Closure factory) {
        this.factory = factory as Factory
    }

    void onConfigure(Closure configure) {
        configures.add(new ClosureBackedAction(configure))
    }
}