/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.project.taskfactory;

import org.gradle.api.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

public interface TaskPropertyActionContext {
    /**
     * Returns the name of this property.
     */
    String getName();

    /**
     * Returns the annotation used to mark the property's type.
     */
    @Nullable
    Class<? extends Annotation> getPropertyType();

    /**
     * Returns the declared type of this property. If the property has an instance variable,
     * then returns the declared type of the instance variable. Otherwise returns the return
     * type of the declaring method.
     */
    Class<?> getValueType();

    /**
     * Sets the instance field of the property.
     */
    void setInstanceVariableField(Field field);

    /**
     * Record annotations encountered during parsing the property's methods and instance field.
     */
    void addAnnotations(Iterable<? extends Annotation> declaredAnnotations);

    /**
     * Returns whether the given annotation is present on the field or any of the methods declaring the property.
     */
    boolean isAnnotationPresent(Class<? extends Annotation> annotationType);

    /**
     * Returns the given annotation if present on the field or any of the methods declaring the property.
     */
    <A extends Annotation> A getAnnotation(Class<A> annotationType);

    /**
     * @return Is this an optional property (value may be null)?
     */
    boolean isOptional();

    /**
     * Sets whether the property allows null values.
     */
    void setOptional(boolean optional);

    /**
     * Specifies the action used to validate the value of this property. This action is only executed when the property
     * value is not null.
     */
    void setValidationAction(ValidationAction action);

    /**
     * Specified the action used to configure the task based on the value of this property. Note that this action is
     * called before the skip and validation actions.
     */
    void setConfigureAction(UpdateAction action);

    /**
     * Process a nested property with the given name.
     */
    void setNestedType(Class<?> type);
}
