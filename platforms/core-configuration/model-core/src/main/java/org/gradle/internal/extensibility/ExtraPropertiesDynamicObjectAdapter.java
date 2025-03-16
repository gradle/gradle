/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.extensibility;

import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public class ExtraPropertiesDynamicObjectAdapter extends AbstractDynamicObject {
    private final ExtraPropertiesExtension extension;
    private final Class<?> delegateType;

    public ExtraPropertiesDynamicObjectAdapter(Class<?> delegateType, ExtraPropertiesExtension extension) {
        this.delegateType = delegateType;
        this.extension = extension;
    }

    @Override
    public String getDisplayName() {
        return delegateType.getName();
    }

    @Override
    public boolean hasProperty(String name) {
        return extension.has(name);
    }

    @Override
    public Map<String, ?> getProperties() {
        return extension.getProperties();
    }

    @Override
    public DynamicInvokeResult tryGetProperty(String name) {
        if (extension.has(name)) {
            return DynamicInvokeResult.found(extension.get(name));
        }
        return DynamicInvokeResult.notFound();
    }

    @Override
    public DynamicInvokeResult trySetProperty(String name, @Nullable Object value) {
        if (extension.has(name)) {
            extension.set(name, value);
            return DynamicInvokeResult.found();
        }
        return DynamicInvokeResult.notFound();
    }
}
