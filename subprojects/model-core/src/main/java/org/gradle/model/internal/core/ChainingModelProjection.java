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

package org.gradle.model.internal.core;

import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.CollectionUtils;

import java.util.List;

public class ChainingModelProjection implements ModelProjection {
    private final Iterable<? extends ModelProjection> projections;

    public ChainingModelProjection(Iterable<? extends ModelProjection> projections) {
        this.projections = projections;
    }

    public <B> boolean canBeViewedAsWritable(final ModelType<B> type) {
        return CollectionUtils.any(projections, new Spec<ModelProjection>() {
            public boolean isSatisfiedBy(ModelProjection projection) {
                return projection.canBeViewedAsWritable(type);
            }
        });
    }

    public <B> boolean canBeViewedAsReadOnly(final ModelType<B> type) {
        return CollectionUtils.any(projections, new Spec<ModelProjection>() {
            public boolean isSatisfiedBy(ModelProjection projection) {
                return projection.canBeViewedAsReadOnly(type);
            }
        });
    }

    private Iterable<String> collectDescriptions(Transformer<Iterable<String>, ModelProjection> transformer) {
        return CollectionUtils.flattenCollections(String.class, CollectionUtils.collect(projections, transformer));
    }

    public Iterable<String> getWritableTypeDescriptions() {
        return collectDescriptions(new Transformer<Iterable<String>, ModelProjection>() {
            public Iterable<String> transform(ModelProjection projection) {
                return projection.getWritableTypeDescriptions();
            }
        });
    }

    public Iterable<String> getReadableTypeDescriptions() {
        return collectDescriptions(new Transformer<Iterable<String>, ModelProjection>() {
            public Iterable<String> transform(ModelProjection projection) {
                return projection.getReadableTypeDescriptions();
            }
        });
    }

    @Nullable
    public <T> ModelView<? extends T> asReadOnly(ModelType<T> type, MutableModelNode node, ModelRuleDescriptor ruleDescriptor) {
        for (ModelProjection projection : projections) {
            ModelView<? extends T> view = projection.asReadOnly(type, node, ruleDescriptor);
            if (view != null) {
                return view;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public <T> ModelView<? extends T> asWritable(ModelType<T> type, MutableModelNode node, ModelRuleDescriptor ruleDescriptor, List<ModelView<?>> inputs) {
        for (ModelProjection projection : projections) {
            ModelView<? extends T> view = projection.asWritable(type, node, ruleDescriptor, inputs);
            if (view != null) {
                return view;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ChainingModelProjection that = (ChainingModelProjection) o;

        return projections.equals(that.projections);
    }

    @Override
    public int hashCode() {
        return projections.hashCode();
    }

    @Override
    public String toString() {
        return "ChainingModelProjection{projections=" + projections + '}';
    }
}
