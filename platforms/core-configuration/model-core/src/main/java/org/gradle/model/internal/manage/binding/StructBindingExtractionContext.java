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

import org.gradle.model.internal.inspect.FormattingValidationProblemCollector;
import org.gradle.model.internal.manage.schema.StructSchema;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class StructBindingExtractionContext<T> implements StructBindingValidationProblemCollector {
    private final StructSchema<T> publicSchema;
    private final Iterable<StructSchema<?>> implementedSchemas;
    private final StructSchema<?> delegateSchema;
    final FormattingValidationProblemCollector problems;

    public StructBindingExtractionContext(StructSchema<T> publicSchema, Iterable<StructSchema<?>> implementedSchemas, StructSchema<?> delegateSchema) {
        this.publicSchema = publicSchema;
        this.implementedSchemas = implementedSchemas;
        this.delegateSchema = delegateSchema;
        this.problems = new FormattingValidationProblemCollector("managed type", publicSchema.getType());
    }

    public StructSchema<T> getPublicSchema() {
        return publicSchema;
    }

    public Iterable<StructSchema<?>> getImplementedSchemas() {
        return implementedSchemas;
    }

    public StructSchema<?> getDelegateSchema() {
        return delegateSchema;
    }

    @Override
    public boolean hasProblems() {
        return problems.hasProblems();
    }

    @Override
    public void add(String problem) {
        problems.add(problem);
    }

    @Override
    public void add(Field field, String problem) {
        problems.add(field, problem);
    }

    @Override
    public void add(WeaklyTypeReferencingMethod<?, ?> method, String problem) {
        add(method.getMethod(), problem);
    }

    @Override
    public void add(WeaklyTypeReferencingMethod<?, ?> method, String role, String problem) {
        add(method.getMethod(), role, problem);
    }

    @Override
    public void add(Method method, String role, String problem) {
        problems.add(method, role, problem);
    }

    @Override
    public void add(Method method, String problem) {
        add(method, null, problem);
    }

    @Override
    public void add(Constructor<?> constructor, String problem) {
        problems.add(constructor, problem);
    }

    @Override
    public void add(String property, String problem) {
        problems.add("Property '" + property + "' is not valid: " + problem);
    }

}
