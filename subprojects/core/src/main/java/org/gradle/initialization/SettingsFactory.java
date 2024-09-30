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
import org.gradle.api.internal.initialization.StandaloneDomainObjectContext;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.extensibility.ExtensibleDynamicObject;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.CloseableServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.internal.service.scopes.SettingsScopeServices;

import java.io.File;
import java.util.Map;

public class SettingsFactory {
    private final Instantiator instantiator;
    private final ServiceRegistry buildScopeServices;
    private final ScriptHandlerFactory scriptHandlerFactory;

    public SettingsFactory(Instantiator instantiator, ServiceRegistry buildScopeServices, ScriptHandlerFactory scriptHandlerFactory) {
        this.instantiator = instantiator;
        this.buildScopeServices = buildScopeServices;
        this.scriptHandlerFactory = scriptHandlerFactory;
    }

    public SettingsState createSettings(
        GradleInternal gradle,
        File settingsDir,
        ScriptSource settingsScript,
        GradleProperties gradleProperties,
        StartParameter startParameter,
        ClassLoaderScope baseClassLoaderScope
    ) {
        ClassLoaderScope classLoaderScope = baseClassLoaderScope.createChild("settings[" + gradle.getIdentityPath() + "]", null);
        SettingsServiceRegistryFactory serviceRegistryFactory = new SettingsServiceRegistryFactory();
        DefaultSettings settings = instantiator.newInstance(
            DefaultSettings.class,
            serviceRegistryFactory,
            gradle,
            classLoaderScope,
            baseClassLoaderScope,
            scriptHandlerFactory.create(settingsScript, classLoaderScope, StandaloneDomainObjectContext.forScript(settingsScript)),
            settingsDir,
            settingsScript,
            startParameter
        );
        Map<String, Object> properties = gradleProperties.getProperties();
        DynamicObject dynamicObject = ((DynamicObjectAware) settings).getAsDynamicObject();
        ((ExtensibleDynamicObject) dynamicObject).addProperties(properties);
        return new SettingsState(settings, serviceRegistryFactory.services);
    }

    private class SettingsServiceRegistryFactory implements ServiceRegistryFactory {
        private CloseableServiceRegistry services;

        @Override
        public ServiceRegistry createFor(Object domainObject) {
            services = SettingsScopeServices.create(buildScopeServices, (SettingsInternal) domainObject);
            return services;
        }
    }
}
