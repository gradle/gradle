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

package org.gradle.model.internal.manage.schema.store;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import net.jcip.annotations.ThreadSafe;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.manage.schema.InvalidManagedModelElementTypeException;
import org.gradle.model.internal.manage.schema.ModelSchema;

import java.util.Deque;
import java.util.List;

@ThreadSafe
public class ModelSchemaExtractor {

    private final static List<? extends ModelSchemaExtractionHandler> EXTRACTION_HANDLERS = ImmutableList.of(new ManagedTypeModelSchemaExtractionHandler(), new ManagedSetSchemaExtractionHandler(),
            new UnmanagedTypeSchemaExtractionHandler());

    private static class Extraction {
        final Deque<ModelSchemaExtractionContext> stack = Lists.newLinkedList();

        void push(List<ModelSchemaExtractionContext> items) {
            stack.addAll(Lists.reverse(items));
        }

        ModelSchemaExtractionContext next() {
            return stack.pop();
        }

        boolean hasNext() {
            return !stack.isEmpty();
        }
    }

    public <T> ModelSchema<T> extract(ModelType<T> type, ModelSchemaCache cache) {
        ModelSchemaExtractionResult<T> schemaExtraction = extractSchema(type, cache, null);

        Extraction extraction = new Extraction();
        pushDependencies(schemaExtraction.getDependencies(), extraction, cache);

        while (extraction.hasNext()) {
            ModelSchemaExtractionContext next = extraction.next();
            ModelSchemaExtractionResult<?> nextSchema;
            try {
                nextSchema = extractSchema(next.getType(), cache, next);
            } catch (InvalidManagedModelElementTypeException e) {
                throw next.wrap(e);
            }

            pushDependencies(nextSchema.getDependencies(), extraction, cache);
        }

        return schemaExtraction.getSchema();
    }

    private <T> void pushDependencies(List<? extends ModelSchemaExtractionContext> dependencies, Extraction extraction, final ModelSchemaCache cache) {
        Iterable<? extends ModelSchemaExtractionContext> pendingDependencies = Iterables.filter(dependencies, new Predicate<ModelSchemaExtractionContext>() {
            public boolean apply(ModelSchemaExtractionContext dependency) {
                return cache.get(dependency.getType()) == null;
            }
        });

        extraction.push(Lists.newLinkedList(pendingDependencies));
    }

    private <T> ModelSchemaExtractionResult<T> extractSchema(final ModelType<T> type, ModelSchemaCache cache, ModelSchemaExtractionContext context) {
        ModelSchema<T> cached = cache.get(type);
        if (cached != null) {
            return new ModelSchemaExtractionResult<T>(cached);
        }

        ModelSchemaExtractionHandler handler = Iterables.find(EXTRACTION_HANDLERS, new Predicate<ModelSchemaExtractionHandler>() {
            public boolean apply(ModelSchemaExtractionHandler candidate) {
                return candidate.getSupportedSuperType().isAssignableFrom(type) && candidate.getSpec().isSatisfiedBy(type);
            }
        });

        ModelSchemaExtractionResult<T> schemaExtraction = handler.extract(type, cache, context);
        cache.set(type, schemaExtraction.getSchema());
        return schemaExtraction;
    }

}
