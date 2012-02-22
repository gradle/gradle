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

import org.gradle.api.internal.*;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ExtensionContainer;

/**
 * Provides a unified, typed, interface to an enhanced DSL object.
 * 
 * This is intended to be used with objects that have been decorated by the class generator.
 * <p>
 * Construction of a DslObject will fail with {@link IllegalArgumentException} if the given object does
 * not meet the requirements of a “DSL Object”. That is, it must:
 * <ul>
 * <li>Implement {@link DynamicObjectAware}</li>
 * <li>Implement {@link ExtensionAware}</li>
 * <li>Implement {@link IConventionAware}</li>
 * <li>Implement {@link HasConvention}</li>
 * </ul>
 */
public class DslObject implements DynamicObjectAware, ExtensionAware, IConventionAware, HasConvention {

    private final DynamicObject dynamicObject;
    private final ExtensionContainer extensionContainer;
    private final ConventionMapping conventionMapping;
    private final Convention convention;
    
    public DslObject(Object object) {
        this.dynamicObject = toType(object, DynamicObjectAware.class).getAsDynamicObject();
        this.extensionContainer = toType(object, ExtensionAware.class).getExtensions();
        this.conventionMapping = toType(object, IConventionAware.class).getConventionMapping();
        this.convention = toType(object, HasConvention.class).getConvention();
    }

    public DynamicObject getAsDynamicObject() {
        return dynamicObject;
    }
    
    public Convention getConvention() {
        return convention;
    }

    public ExtensionContainer getExtensions() {
        return extensionContainer;
    }

    public ConventionMapping getConventionMapping() {
        return conventionMapping;
    }

    private static <T> T toType(Object delegate, Class<T> type) {
        if (type.isInstance(delegate)) {
            return type.cast(delegate);
        } else {
            throw new IllegalArgumentException(
                    String.format("Cannot create DslObject for '%s' as it does not implement '%s' (it is not a DSL object)", delegate, type.getName())
            );
        }
    }

}
