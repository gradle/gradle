/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.model.internal.manage.binding;

import org.gradle.model.internal.inspect.ValidationProblemCollector;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;

import java.lang.reflect.Method;

public interface StructBindingValidationProblemCollector extends ValidationProblemCollector {
    void add(WeaklyTypeReferencingMethod<?, ?> method, String problem);

    void add(WeaklyTypeReferencingMethod<?, ?> method, String role, String problem);

    void add(Method method, String problem);

    /**
     * Adds a problem with a property.
     */
    void add(String property, String problem);
}
