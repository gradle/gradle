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

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import net.jcip.annotations.ThreadSafe;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.Set;

@ThreadSafe
public class DefaultModelRegistration implements ModelRegistration {
    private final ModelPath path;
    private final ModelRuleDescriptor descriptor;
    private final boolean service;
    private final boolean hidden;
    private final ListMultimap<ModelActionRole, ? extends ModelAction> actions;

    public DefaultModelRegistration(
        ModelPath path,
        ModelRuleDescriptor descriptor,
        boolean service,
        boolean hidden,
        Multimap<ModelActionRole, ? extends ModelAction> actions) {
        this.path = path;
        this.descriptor = descriptor;
        this.service = service;
        this.hidden = hidden;
        this.actions = ImmutableListMultimap.copyOf(actions);
    }

    public ModelPath getPath() {
        return path;
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
    public boolean isService() {
        return service;
    }

    @Override
    public boolean isHidden() {
        return hidden;
    }

    @Override
    public ModelRuleDescriptor getDescriptor() {
        return descriptor;
    }
}
