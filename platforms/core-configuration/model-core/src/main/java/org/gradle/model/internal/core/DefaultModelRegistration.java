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

import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;
import javax.annotation.concurrent.ThreadSafe;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

@ThreadSafe
public class DefaultModelRegistration implements ModelRegistration {
    private final ModelPath path;
    private final ModelRuleDescriptor descriptor;
    private final boolean hidden;
    private final Multimap<ModelActionRole, ? extends ModelAction> actions;

    public DefaultModelRegistration(ModelPath path, ModelRuleDescriptor descriptor,
                                    boolean hidden, Multimap<ModelActionRole, ? extends ModelAction> actions) {
        this.path = Preconditions.checkNotNull(path, "path");
        this.descriptor = Preconditions.checkNotNull(descriptor, "descriptor");
        this.hidden = hidden;
        this.actions = Preconditions.checkNotNull(actions, "actions");
    }

    @Override
    public ModelPath getPath() {
        return path;
    }

    @Override
    public Multimap<ModelActionRole, ? extends ModelAction> getActions() {
        return actions;
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
