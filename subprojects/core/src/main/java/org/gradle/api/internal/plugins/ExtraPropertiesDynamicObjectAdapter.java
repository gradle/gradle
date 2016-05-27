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

import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.GetPropertyResult;
import org.gradle.internal.metaobject.InvokeMethodResult;
import org.gradle.internal.metaobject.SetPropertyResult;
import org.gradle.util.DeprecationLogger;

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
    public void getProperty(String name, GetPropertyResult result) {
        if (extension.has(name)) {
            result.result(extension.get(name));
        }
    }

    @Override
    public void setProperty(String name, Object value, SetPropertyResult result) {
        if (extension.has(name)) {
            extension.set(name, value);
            result.found();
        }
    }

    @Override
    public boolean hasMethod(String name, Object... arguments) {
        return (name.equals("get") && arguments.length == 1 && arguments[0] instanceof String)
            || (name.equals("set") && arguments.length == 2 && arguments[0] instanceof String)
            || (name.equals("has") && arguments.length == 1 && arguments[0] instanceof String);
    }

    @Override
    public void invokeMethod(String name, InvokeMethodResult result, Object... arguments) {
        if(name.equals("get") && arguments.length == 1 && arguments[0] instanceof String) {
            DeprecationLogger.nagUserOfReplacedMethod("get()", "getProperty() or ext.get()");
            result.result(extension.get((String) arguments[0]));
        } else if (name.equals("set") && arguments.length == 2 && arguments[0] instanceof String) {
            DeprecationLogger.nagUserOfReplacedMethod("set()", "setProperty() or ext.set()");
            extension.set((String) arguments[0], arguments[1]);
            result.result(null);
        } else if (name.equals("has") && arguments.length == 1 && arguments[0] instanceof String) {
            DeprecationLogger.nagUserOfReplacedMethod("has()", "hasProperty() or ext.has()");
            result.result(extension.has((String) arguments[0]));
        }
    }
}
