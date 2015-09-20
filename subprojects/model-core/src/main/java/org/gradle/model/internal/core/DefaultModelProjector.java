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

package org.gradle.model.internal.core;

import com.google.common.collect.ImmutableSet;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DefaultModelProjector implements ModelProjector {

    private final ModelPath path;
    private final ModelRuleDescriptor descriptor;
    private final Set<ModelProjection> projections;

    public DefaultModelProjector(ModelPath path, ModelRuleDescriptor descriptor, Collection<ModelProjection> projections) {
        this.path = path;
        this.descriptor = descriptor;
        this.projections = ImmutableSet.copyOf(projections);
    }

    @Override
    public ModelPath getPath() {
        return path;
    }

    @Override
    public Set<? extends ModelProjection> getProjections() {
        return projections;
    }

    @Override
    public List<ModelReference<?>> getInputs() {
        return Collections.emptyList();
    }

    @Override
    public ModelRuleDescriptor getDescriptor() {
        return descriptor;
    }
}
