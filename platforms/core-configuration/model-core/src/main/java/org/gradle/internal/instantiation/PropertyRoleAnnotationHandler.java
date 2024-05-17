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

package org.gradle.internal.instantiation;

import org.gradle.internal.state.ModelObject;

import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * Responsible for defining the behaviour of a particular annotation used for defining the role of a property.
 *
 * <p>Implementations must be registered as global scoped services.</p>
 */
public interface PropertyRoleAnnotationHandler {
    Set<Class<? extends Annotation>> getAnnotationTypes();

    void applyRoleTo(ModelObject owner, Object target);
}
