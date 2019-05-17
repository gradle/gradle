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

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import javax.annotation.Nullable;

public class ChainingModelProjection implements ModelProjection {
    private final Iterable<? extends ModelProjection> projections;

    public ChainingModelProjection(Iterable<? extends ModelProjection> projections) {
        this.projections = projections;
    }

    @Override
    public <T> boolean canBeViewedAs(ModelType<T> type) {
        for (ModelProjection projection : projections) {
            if (projection.canBeViewedAs(type)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterable<String> getTypeDescriptions(final MutableModelNode node) {
        return Iterables.concat(Iterables.transform(projections, new Function<ModelProjection, Iterable<String>>() {
            @Override
            public Iterable<String> apply(ModelProjection projection) {
                return projection.getTypeDescriptions(node);
            }
        }));
    }

    @Override
    @Nullable
    public <T> ModelView<? extends T> asImmutable(ModelType<T> type, MutableModelNode node, ModelRuleDescriptor ruleDescriptor) {
        for (ModelProjection projection : projections) {
            ModelView<? extends T> view = projection.asImmutable(type, node, ruleDescriptor);
            if (view != null) {
                return view;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public <T> ModelView<? extends T> asMutable(ModelType<T> type, MutableModelNode node, ModelRuleDescriptor ruleDescriptor) {
        for (ModelProjection projection : projections) {
            ModelView<? extends T> view = projection.asMutable(type, node, ruleDescriptor);
            if (view != null) {
                return view;
            }
        }
        return null;
    }

    @Override
    public Optional<String> getValueDescription(MutableModelNode modelNodeInternal) {
        for (ModelProjection projection : projections) {
            Optional<String> projectionValueDescription = projection.getValueDescription(modelNodeInternal);
            if (projectionValueDescription.isPresent()) {
                return projectionValueDescription;
            }
        }
        return Optional.absent();
    }

    @Override
    public String toString() {
        return "ChainingModelProjection{projections=" + projections + '}';
    }
}
