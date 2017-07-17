/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.manage.schema.extract;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.internal.Actions;
import org.gradle.model.internal.inspect.FormattingValidationProblemCollector;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.type.ModelType;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class DefaultModelSchemaExtractionContext<T> implements ModelSchemaExtractionContext<T> {

    private final DefaultModelSchemaExtractionContext<?> parent;
    private final ModelType<T> type;
    private final String description;
    private final Action<? super ModelSchema<T>> validator;
    private ModelSchema<T> result;
    private final List<DefaultModelSchemaExtractionContext<?>> children = Lists.newArrayList();
    private final FormattingValidationProblemCollector problems;

    private DefaultModelSchemaExtractionContext(DefaultModelSchemaExtractionContext<?> parent, ModelType<T> type, String description, Action<? super ModelSchema<T>> validator) {
        this.parent = parent;
        this.type = type;
        this.description = description;
        this.problems = new FormattingValidationProblemCollector("model element type", type);
        this.validator = validator;
    }

    public static <T> DefaultModelSchemaExtractionContext<T> root(ModelType<T> type) {
        return new DefaultModelSchemaExtractionContext<T>(null, type, null, null);
    }

    /**
     * null if this is the root of the extraction
     */
    @Nullable
    public DefaultModelSchemaExtractionContext<?> getParent() {
        return parent;
    }

    @Override
    public ModelType<T> getType() {
        return type;
    }

    public FormattingValidationProblemCollector getProblems() {
        return problems;
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

    public String getDescription() {
        return description == null ? type.toString() : (description + " (" + type + ")");
    }

    public List<DefaultModelSchemaExtractionContext<?>> getChildren() {
        return children;
    }

    @Override
    public <C> DefaultModelSchemaExtractionContext<C> child(ModelType<C> type, String description) {
        return child(type, description, Actions.doNothing());
    }

    @Override
    public <C> DefaultModelSchemaExtractionContext<C> child(ModelType<C> type, String description, Action<? super ModelSchema<C>> validator) {
        DefaultModelSchemaExtractionContext<C> childContext = new DefaultModelSchemaExtractionContext<C>(this, type, description, validator);
        children.add(childContext);
        return childContext;
    }

    @Nullable
    public ModelSchema<T> getResult() {
        return result;
    }

    @Override
    public void found(ModelSchema<T> result) {
        this.result = result;
    }

    public void validate(ModelSchema<T> schema) {
        if (validator != null) {
            validator.execute(schema);
        }
    }
}
