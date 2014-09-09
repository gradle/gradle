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

package org.gradle.initialization;

import org.gradle.StartParameter;
import org.gradle.api.internal.*;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;
import java.util.Map;

public class SettingsFactory {
    private final Instantiator instantiator;
    private final ServiceRegistryFactory serviceRegistryFactory;

    public SettingsFactory(Instantiator instantiator, ServiceRegistryFactory serviceRegistryFactory) {
        this.instantiator = instantiator;
        this.serviceRegistryFactory = serviceRegistryFactory;
    }

    public SettingsInternal createSettings(GradleInternal gradle, File settingsDir, ScriptSource settingsScript,
                                           Map<String, String> gradleProperties, StartParameter startParameter,
                                           ClassLoaderScope rootClassLoaderScope) {

        DefaultSettings settings = instantiator.newInstance(DefaultSettings.class,
                serviceRegistryFactory, gradle, rootClassLoaderScope.createChild(), rootClassLoaderScope, settingsDir, settingsScript, startParameter
        );

        DynamicObject dynamicObject = ((DynamicObjectAware) settings).getAsDynamicObject();
        ((ExtensibleDynamicObject) dynamicObject).addProperties(gradleProperties);
        return settings;
    }
}
