/*
 * Copyright 2012 the original author or authors.
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

import groovy.lang.MissingPropertyException;
import org.gradle.api.internal.BeanDynamicObject;
import org.gradle.api.internal.DynamicObject;
import org.gradle.api.plugins.DynamicPropertiesExtension;
import org.gradle.util.DeprecationLogger;

import java.util.Map;

public class DynamicPropertiesDynamicObjectAdapter extends BeanDynamicObject {

    private final DynamicPropertiesExtension extension;
    private final Object delegate;
    private final DynamicObject dynamicOwner;

    public DynamicPropertiesDynamicObjectAdapter(Object delegate, DynamicObject dynamicOwner, DynamicPropertiesExtension extension) {
        super(extension);
        this.delegate = delegate;
        this.dynamicOwner = dynamicOwner;
        this.extension = extension;
    }

    public boolean hasProperty(String name) {
        return super.hasProperty(name) || extension.has(name);
    }

    public Map<String, ?> getProperties() {
        return extension.getProperties();
    }

    @Override
    public void setProperty(String name, Object value) throws MissingPropertyException {
        if (!dynamicOwner.hasProperty(name)) {

            DeprecationLogger.nagUserWith(
                    String.format(
                            "Adding dynamic properties by assignment has been deprecated. "
                                    + "An attempt was made to create a dynamic property named '%s' on the object '%s' (class: %s) with the value '%s'. "
                                    + "You should set the property on the target's 'ext' object. "
                                    + "e.g. <target>.ext.<property> = <value> (see '%s' in the Gradle DSL reference for more information)",
                            name, delegate, delegate.getClass().getSimpleName(), value, DynamicPropertiesExtension.class.getSimpleName())
            );
        }

        super.setProperty(name, value);
    }
}
