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

import com.google.common.collect.*;
import net.jcip.annotations.ThreadSafe;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.List;
import java.util.Set;

@ThreadSafe
public class ProjectionBackedModelCreator implements ModelCreator {
    private final ModelPath path;
    private final ModelRuleDescriptor descriptor;
    private final boolean ephemeral;
    private final ModelProjection projection;
    private final List<ModelProjection> projections;
    private final ListMultimap<ModelActionRole, ? extends ModelAction> actions;

    public ProjectionBackedModelCreator(
        ModelPath path,
        ModelRuleDescriptor descriptor,
        boolean ephemeral,
        final boolean hidden,
        Iterable<? extends ModelProjection> initialProjections,
        Multimap<ModelActionRole, ? extends ModelAction> actions) {
        this.path = path;
        this.descriptor = descriptor;
        this.ephemeral = ephemeral;
        this.projections = Lists.newArrayList(initialProjections);
        this.projection = new ChainingModelProjection(projections);

        ImmutableListMultimap.Builder<ModelActionRole, ModelAction> actionsBuilder = ImmutableListMultimap.builder();
        actionsBuilder.putAll(actions);
        actionsBuilder.put(ModelActionRole.DefineProjections, new AbstractModelAction<Object>(ModelReference.of(path), descriptor) {
            @Override
            public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
                modelNode.setHidden(hidden);
            }
        });
        this.actions = actionsBuilder.build();
    }

    public ModelPath getPath() {
        return path;
    }

    public ModelPromise getPromise() {
        return projection;
    }

    public ModelAdapter getAdapter() {
        return projection;
    }

    @Override
    public ModelProjection getProjection() {
        return projection;
    }

    @Override
    public ListMultimap<ModelActionRole, ? extends ModelAction> getActions() {
        return actions;
    }

    @Override
    public Set<? extends ModelReference<?>> getInputs() {
        final ImmutableSet.Builder<ModelReference<?>> builder = ImmutableSet.builder();
        for (ModelAction action : actions.values()) {
            builder.addAll(action.getInputs());
        }
        return builder.build();
    }

    @Override
    public boolean isEphemeral() {
        return ephemeral;
    }

    @Override
    public ModelRuleDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public void addProjection(ModelProjection projection) {
        projections.add(projection);
    }
}
