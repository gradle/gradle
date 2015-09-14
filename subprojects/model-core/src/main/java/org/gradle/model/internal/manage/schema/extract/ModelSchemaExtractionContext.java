/*
 * Copyright 2014 the original author or authors.
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
import net.jcip.annotations.NotThreadSafe;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.internal.Actions;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.type.ModelType;

import java.util.List;

@NotThreadSafe
public class ModelSchemaExtractionContext<T> {

    private final ModelSchemaExtractionContext<?> parent;
    private final ModelType<T> type;
    private final String description;
    private final List<Action<? super ModelSchema<T>>> validators;
    private ModelSchema<T> result;
    private final List<ModelSchemaExtractionContext<?>> children = Lists.newArrayList();

    private ModelSchemaExtractionContext(ModelSchemaExtractionContext<?> parent, ModelType<T> type, String description, Action<? super ModelSchema<T>> validator) {
        this.parent = parent;
        this.type = type;
        this.description = description;
        this.validators = Lists.newArrayListWithCapacity(2);
        if (validator != null) {
            validators.add(validator);
        }
    }

    public static <T> ModelSchemaExtractionContext<T> root(ModelType<T> type) {
        return new ModelSchemaExtractionContext<T>(null, type, null, null);
    }

    /**
     * null if this is the root of the extraction
     */
    @Nullable
    public ModelSchemaExtractionContext<?> getParent() {
        return parent;
    }

    public ModelType<T> getType() {
        return type;
    }

    public String getDescription() {
        return description == null ? type.toString() : String.format("%s (%s)", description, type);
    }

    public List<ModelSchemaExtractionContext<?>> getChildren() {
        return children;
    }

    public <C> ModelSchemaExtractionContext<C> child(ModelType<C> type, String description) {
        return child(type, description, Actions.doNothing());
    }

    public <C> ModelSchemaExtractionContext<C> child(ModelType<C> type, String description, Action<? super ModelSchema<C>> validator) {
        ModelSchemaExtractionContext<C> childContext = new ModelSchemaExtractionContext<C>(this, type, description, validator);
        children.add(childContext);
        return childContext;
    }

    @Nullable
    public ModelSchema<T> getResult() {
        return result;
    }

    public void found(ModelSchema<T> result) {
        this.result = result;
    }

    public void validate(ModelSchema<T> schema) {
        for (Action<? super ModelSchema<T>> validator : validators) {
            validator.execute(schema);
        }
    }

    public void addValidator(Action<? super ModelSchema<T>> validator) {
        validators.add(validator);
    }
}
