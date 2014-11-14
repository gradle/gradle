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

import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.CollectionUtils;

public class ProjectionBackedModelPromise implements ModelPromise {

    private final Iterable<? extends ModelProjection> projections;

    public ProjectionBackedModelPromise(Iterable<? extends ModelProjection> projections) {
        this.projections = projections;
    }

    public <B> boolean asWritable(final ModelType<B> type) {
        return CollectionUtils.any(projections, new Spec<ModelProjection>() {
            public boolean isSatisfiedBy(ModelProjection projection) {
                return projection.canBeViewedAsWritable(type);
            }
        });
    }

    public <B> boolean asReadOnly(final ModelType<B> type) {
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

}
