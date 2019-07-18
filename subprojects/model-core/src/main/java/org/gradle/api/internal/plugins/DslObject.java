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

package org.gradle.api.internal.plugins;

import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.api.internal.HasConvention;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.internal.metaobject.DynamicObject;

import static org.gradle.internal.Cast.uncheckedCast;

/**
 * Provides a unified, typed, interface to an enhanced DSL object.
 *
 * This is intended to be used with objects that have been decorated by the class generator.
 * <p>
 * Accessing each “aspect” of a DSL object may fail (with an {@link IllegalStateException}) if the DSL
 * object does not have that functionality. For example, calling {@link #getConventionMapping()} will fail
 * if the backing object does not implement {@link IConventionAware}.
 */
public class DslObject implements DynamicObjectAware, ExtensionAware, IConventionAware, HasConvention {

    private DynamicObject dynamicObject;
    private ExtensionContainer extensionContainer;
    private ConventionMapping conventionMapping;
    private Convention convention;

    private final Object object;

    public DslObject(Object object) {
        this.object = object;
    }

    @Override
    public DynamicObject getAsDynamicObject() {
        if (dynamicObject == null) {
            this.dynamicObject = toType(object, DynamicObjectAware.class).getAsDynamicObject();
        }
        return dynamicObject;
    }

    @Override
    public Convention getConvention() {
        if (convention == null) {
            this.convention = toType(object, HasConvention.class).getConvention();
        }
        return convention;
    }

    @Override
    public ExtensionContainer getExtensions() {
        if (extensionContainer == null) {
            this.extensionContainer = toType(object, ExtensionAware.class).getExtensions();
        }
        return extensionContainer;
    }

    @Override
    public ConventionMapping getConventionMapping() {
        if (conventionMapping == null) {
            this.conventionMapping = toType(object, IConventionAware.class).getConventionMapping();
        }
        return conventionMapping;
    }

    public Class<?> getDeclaredType() {
        return getPublicType().getConcreteClass();
    }

    public TypeOf<Object> getPublicType() {
        if (object instanceof HasPublicType) {
            return uncheckedCast(((HasPublicType) object).getPublicType());
        }
        return TypeOf.<Object>typeOf(GeneratedSubclasses.unpackType(object));
    }

    public Class<?> getImplementationType() {
        return GeneratedSubclasses.unpackType(object);
    }

    private static <T> T toType(Object delegate, Class<T> type) {
        if (type.isInstance(delegate)) {
            return type.cast(delegate);
        } else {
            throw new IllegalStateException(
                    String.format("Cannot create DslObject for '%s' (class: %s) as it does not implement '%s' (it is not a DSL object)",
                            delegate, delegate.getClass().getSimpleName(), type.getName())
            );
        }
    }

}
