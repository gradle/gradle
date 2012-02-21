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
import org.gradle.api.plugins.DynamicExtension;
import org.gradle.util.DeprecationLogger;

import java.util.Map;

public class DynamicExtensionDynamicObjectAdapter extends BeanDynamicObject {

    public static final boolean WARN_ON_PROPERTY_CREATION = false;

    private final DynamicExtension extension;
    private final Object delegate;

    public DynamicExtensionDynamicObjectAdapter(Object delegate, DynamicExtension extension) {
        super(extension);
        this.delegate = delegate;
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
        if (!hasProperty(name) && WARN_ON_PROPERTY_CREATION) {
            DeprecationLogger.nagUserWith(
                    String.format(
                            "Adding dynamic properties by assignment has been deprecated. "
                                    + "An attempt was made to create a dynamic property named '%s' on the object '%s' with the value '%s'. "
                                    + "You should set the property on the target's 'ext' object. "
                                    + "e.g. <target>.ext.<property> = <value>",
                            name, delegate, value)
            );
        }

        super.setProperty(name, value);
    }
}
