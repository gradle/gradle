/*
 * Copyright 2020 the original author or authors.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import groovy.lang.DelegatingMetaClass;
import groovy.lang.GroovyObject;
import groovy.lang.MetaClass;
import groovy.lang.MetaProperty;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.internal.extensibility.ExtensibleDynamicObject;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.DynamicInvokeResult;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toSet;
import static org.gradle.internal.metaobject.DynamicInvokeResult.found;
import static org.gradle.internal.metaobject.DynamicInvokeResult.notFound;

class DefaultGradleProperties implements GradleProperties {
    private final Map<String, String> defaultProperties;
    private final Map<String, String> overrideProperties;
    private final ImmutableMap<String, String> gradleProperties;
    private final Listener listener;

    public DefaultGradleProperties(
        Map<String, String> defaultProperties,
        Map<String, String> overrideProperties,
        Listener listener
    ) {
        this.defaultProperties = defaultProperties;
        this.overrideProperties = overrideProperties;
        this.listener = listener;
        gradleProperties = immutablePropertiesWith(ImmutableMap.of());
    }

    @Nullable
    @Override
    public Object find(String propertyName) {
        Object value = gradleProperties.get(propertyName);
        listener.onPropertyRead(propertyName, value);
        return value;
    }

    @Override
    public Set<String> getPropertyNames() {
        return gradleProperties.keySet();
    }

    @Override
    public Map<String, String> mergeProperties(Map<String, String> properties) {
        return properties.isEmpty()
            ? gradleProperties
            : immutablePropertiesWith(properties);
    }

    ImmutableMap<String, String> immutablePropertiesWith(Map<String, String> properties) {
        return ImmutableMap.copyOf(mergePropertiesWith(properties));
    }

    Map<String, String> mergePropertiesWith(Map<String, String> properties) {
        Map<String, String> result = new HashMap<>();
        result.putAll(defaultProperties);
        result.putAll(properties);
        result.putAll(overrideProperties);
        return result;
    }

    /**
     * Makes Gradle properties visible dynamically via {@link ExtensibleDynamicObject} chaining and Groovy {@link MetaClass}
     * delegation in order to track their access at configuration time and automatically identify them as
     * configuration cache inputs.
     *
     * @see GradleProperties.Listener
     */
    public static void exposeGradlePropertiesDynamically(GradleProperties gradleProperties, Object object) {
        exposeGradlePropertiesViaGroovyMetaClass(gradleProperties, object);
        exposeGradlePropertiesAsDynamicObjectParent(gradleProperties, object);
    }

    private static void exposeGradlePropertiesViaGroovyMetaClass(GradleProperties gradleProperties, Object object) {
        final GroovyObject groovyObject = (GroovyObject) object;
        groovyObject.setMetaClass(
            delegatingMetaClassFor(gradleProperties, groovyObject.getMetaClass())
        );
    }

    private static void exposeGradlePropertiesAsDynamicObjectParent(GradleProperties gradleProperties, Object object) {
        extensibleDynamicObjectOf(object).setParent(
            dynamicObjectFor(gradleProperties)
        );
    }

    private static DelegatingMetaClass delegatingMetaClassFor(GradleProperties gradleProperties, MetaClass metaClass) {
        return new DelegatingMetaClass(metaClass) {
            @Override
            public List<MetaProperty> getProperties() {
                List<MetaProperty> groovyProperties = super.getProperties();
                Set<String> groovyPropertyNames = groovyProperties.stream().map(MetaProperty::getName).collect(toSet());
                Sets.difference(gradleProperties.getPropertyNames(), groovyPropertyNames).forEach(gradleOnlyProperty -> {
                    groovyProperties.add(metaProperty(gradleOnlyProperty));
                });
                return groovyProperties;
            }

            private MetaProperty metaProperty(String name) {
                return new MetaProperty(name, String.class) {
                    @Nullable
                    @Override
                    public Object getProperty(Object object) {
                        return gradleProperties.find(getName());
                    }

                    @Override
                    public void setProperty(Object object, Object newValue) {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    private static ExtensibleDynamicObject extensibleDynamicObjectOf(Object o) {
        return (ExtensibleDynamicObject) ((DynamicObjectAware) o).getAsDynamicObject();
    }

    private static AbstractDynamicObject dynamicObjectFor(GradleProperties gradleProperties) {
        return new AbstractDynamicObject() {
            @Override
            public String getDisplayName() {
                return "Gradle properties";
            }

            @Nullable
            @Override
            public Class<?> getPublicType() {
                return GradleProperties.class;
            }

            @Override
            public boolean hasProperty(String name) {
                return gradleProperties.find(name) != null;
            }

            @Override
            public Map<String, ?> getProperties() {
                // TODO:configuration-cache track property access here
                return gradleProperties.mergeProperties(emptyMap());
            }

            @Override
            public DynamicInvokeResult tryGetProperty(String name) {
                Object value = gradleProperties.find(name);
                return value != null ? found(value) : notFound();
            }
        };
    }
}
