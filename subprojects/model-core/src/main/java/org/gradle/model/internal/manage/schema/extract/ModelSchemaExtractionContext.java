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
import org.gradle.model.internal.type.ModelType;

import java.util.List;

@NotThreadSafe
public class ModelSchemaExtractionContext<T> {

    private final ModelSchemaExtractionContext<?> parent;
    private final ModelType<T> type;
    private final String description;
    private final List<Action<? super ModelSchemaExtractionContext<T>>> validators;

    private ModelSchemaExtractionContext(ModelSchemaExtractionContext<?> parent, ModelType<T> type, String description, Action<? super ModelSchemaExtractionContext<T>> validator) {
        this.parent = parent;
        this.type = type;
        this.description = description;
        this.validators = Lists.newArrayListWithCapacity(2);

        validators.add(validator);
    }

    public static <T> ModelSchemaExtractionContext<T> root(ModelType<T> type) {
        return new ModelSchemaExtractionContext<T>(null, type, null, Actions.doNothing());
    }

    public static <T> ModelSchemaExtractionContext<T> root(ModelType<T> type, Action<? super ModelSchemaExtractionContext<T>> validator) {
        return new ModelSchemaExtractionContext<T>(null, type, null, validator);
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

    public <C> ModelSchemaExtractionContext<C> child(ModelType<C> type, String description) {
        return child(type, description, Actions.doNothing());
    }

    public <C> ModelSchemaExtractionContext<C> child(ModelType<C> type, String description, Action<? super ModelSchemaExtractionContext<C>> validator) {
        return new ModelSchemaExtractionContext<C>(this, type, description, validator);
    }

    public void validate() {
        for (Action<? super ModelSchemaExtractionContext<T>> validator : validators) {
            validator.execute(this);
        }
    }

    public void addValidator(Action<? super ModelSchemaExtractionContext<T>> validator) {
        validators.add(validator);
    }
}
