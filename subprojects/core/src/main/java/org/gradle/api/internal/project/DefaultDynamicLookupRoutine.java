/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.project;

import groovy.lang.MissingPropertyException;
import org.gradle.internal.Factory;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.gradle.internal.metaobject.DynamicObject;

import javax.annotation.Nullable;
import java.util.Map;

public class DefaultDynamicLookupRoutine implements DynamicLookupRoutine {
    @Override
    public Object property(DynamicObject receiver, String propertyName) throws MissingPropertyException {
        return receiver.getProperty(propertyName);
    }

    @Override
    public @Nullable Object findProperty(DynamicObject receiver, String propertyName) {
        DynamicInvokeResult dynamicInvokeResult = receiver.tryGetProperty(propertyName);
        return dynamicInvokeResult.isFound() ? dynamicInvokeResult.getValue() : null;
    }

    @Override
    public void setProperty(DynamicObject receiver, String name, @Nullable Object value) {
        receiver.setProperty(name, value);
    }

    @Override
    public boolean hasProperty(DynamicObject receiver, String propertyName) {
        return receiver.hasProperty(propertyName);
    }

    @Override
    public Map<String, ?> getProperties(DynamicObject receiver) {
        return DeprecationLogger.whileDisabled((Factory<Map<String, ?>>) () -> receiver.getProperties());
    }

    @Nullable
    @Override
    public Object invokeMethod(DynamicObject receiver, String name, Object... args) {
        return receiver.invokeMethod(name, args);
    }
}
