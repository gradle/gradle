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

import net.jcip.annotations.NotThreadSafe;
import org.gradle.api.Action;
import org.gradle.model.ModelViewClosedException;
import org.gradle.model.ReadOnlyModelViewException;
import org.gradle.model.WriteOnlyModelViewException;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

@NotThreadSafe
public class DefaultModelViewState implements ModelViewState {

    private final ModelPath path;
    private final ModelType<?> type;
    private final ModelRuleDescriptor ruleDescriptor;
    private boolean closed;
    private final boolean mutable;
    private final boolean canReadChildren;

    public DefaultModelViewState(ModelPath path, ModelType<?> type, ModelRuleDescriptor ruleDescriptor, boolean mutable, boolean canReadChildren) {
        this.path = path;
        this.type = type;
        this.ruleDescriptor = ruleDescriptor;
        this.mutable = mutable;
        this.canReadChildren = canReadChildren;
    }

    public void close() {
        closed = true;
    }

    public Action<Object> closer() {
        return new Action<Object>() {
            @Override
            public void execute(Object o) {
                close();
            }
        };
    }

    @Override
    public void assertCanMutate() {
        if (closed) {
            throw new ModelViewClosedException(path, type, ruleDescriptor);
        }
        if (!mutable) {
            throw new ReadOnlyModelViewException(path, type, ruleDescriptor);
        }
    }

    @Override
    public void assertCanReadChildren() {
        if (!canReadChildren) {
            throw new WriteOnlyModelViewException(null, path, type, ruleDescriptor);
        }
    }

    @Override
    public void assertCanReadChild(String name) {
        if (!canReadChildren) {
            throw new WriteOnlyModelViewException(name, path, type, ruleDescriptor);
        }
    }

    @Override
    public boolean isCanMutate() {
        return mutable && !closed;
    }

    public boolean isClosed() {
        return closed;
    }
}
