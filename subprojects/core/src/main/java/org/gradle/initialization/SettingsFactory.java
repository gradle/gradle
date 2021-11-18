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

import com.google.common.collect.Sets;
import groovy.lang.DelegatingMetaClass;
import groovy.lang.GroovyObject;
import groovy.lang.MetaClass;
import groovy.lang.MetaProperty;
import org.gradle.StartParameter;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.extensibility.ExtensibleDynamicObject;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        final GroovyObject groovyObject = (GroovyObject) settings;
        MetaClass metaClass = groovyObject.getMetaClass();
        groovyObject.setMetaClass(
            delegatingMetaClassFor(gradleProperties, metaClass)
        );
        extensibleDynamicObjectOf(settings).setParent(
            dynamicObjectFor(gradleProperties)
        );
        return settings;
    }

    private DelegatingMetaClass delegatingMetaClassFor(GradleProperties gradleProperties, MetaClass metaClass) {
        return new DelegatingMetaClass(metaClass) {
            @Override
            public List<MetaProperty> getProperties() {
                List<MetaProperty> groovyProperties = super.getProperties();
                Set<String> groovyPropertyNames = groovyProperties.stream().map(MetaProperty::getName).collect(Collectors.toSet());
                Sets.difference(gradleProperties.getPropertyNames(), groovyPropertyNames).forEach(s -> {
                    groovyProperties.add(
                        new MetaProperty(s, Object.class) {
                            @Override
                            public Object getProperty(Object object) {
                                return gradleProperties.find(getName());
                            }

                            @Override
                            public void setProperty(Object object, Object newValue) {
                                throw new UnsupportedOperationException();
                            }
                        }
                    );
                });
                return groovyProperties;
            }
        };
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

    private static ExtensibleDynamicObject extensibleDynamicObjectOf(Object o) {
        return (ExtensibleDynamicObject) ((DynamicObjectAware) o).getAsDynamicObject();
    }
}
