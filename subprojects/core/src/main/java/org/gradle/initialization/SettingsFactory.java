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
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.extensibility.ExtensibleDynamicObject;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;

import java.io.File;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static org.gradle.internal.metaobject.DynamicInvokeResult.found;
import static org.gradle.internal.metaobject.DynamicInvokeResult.notFound;

public class SettingsFactory {
    private final Instantiator instantiator;
    private final ServiceRegistryFactory serviceRegistryFactory;
    private final ScriptHandlerFactory scriptHandlerFactory;

    public SettingsFactory(Instantiator instantiator, ServiceRegistryFactory serviceRegistryFactory, ScriptHandlerFactory scriptHandlerFactory) {
        this.instantiator = instantiator;
        this.serviceRegistryFactory = serviceRegistryFactory;
        this.scriptHandlerFactory = scriptHandlerFactory;
    }

    public SettingsInternal createSettings(
        GradleInternal gradle,
        File settingsDir,
        ScriptSource settingsScript,
        GradleProperties gradleProperties,
        StartParameter startParameter,
        ClassLoaderScope baseClassLoaderScope
    ) {
        ClassLoaderScope classLoaderScope = baseClassLoaderScope.createChild("settings[" + gradle.getIdentityPath() + "]");
        DefaultSettings settings = instantiator.newInstance(
            DefaultSettings.class,
            serviceRegistryFactory,
            gradle,
            classLoaderScope,
            baseClassLoaderScope,
            scriptHandlerFactory.create(settingsScript, classLoaderScope),
            settingsDir,
            settingsScript,
            startParameter
        );
        extensibleDynamicObjectOf(settings).setParent(
            dynamicObjectFor(gradleProperties)
        );
        return settings;
    }

    private AbstractDynamicObject dynamicObjectFor(GradleProperties gradleProperties) {
        return new AbstractDynamicObject() {
            @Override
            public String getDisplayName() {
                return "Gradle properties";
            }

            @Override
            public DynamicInvokeResult tryGetProperty(String name) {
                String value = gradleProperties.find(name);
                return value != null ? found(value) : notFound();
            }
        };
    }

    private ExtensibleDynamicObject extensibleDynamicObjectOf(Object o) {
        return (ExtensibleDynamicObject) ((DynamicObjectAware) o).getAsDynamicObject();
    }
}
