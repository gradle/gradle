/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.Set;

public interface ModelNode {

    boolean hasLink(String name);

    boolean hasLink(String name, ModelType<?> type);

    // Note: order is crucial here
    public enum State {
        Known(true),
        Created(true),
        DefaultsApplied(true),
        Initialized(true),
        Mutated(true),
        Finalized(false),
        SelfClosed(false),
        GraphClosed(false);

        public final boolean mutable;

        State(boolean mutable) {
            this.mutable = mutable;
        }
    }

    boolean isEphemeral();

    ModelPath getPath();

    ModelRuleDescriptor getDescriptor();

    State getState();

    /**
     * Creates a read-only view over this node's value.
     *
     * Callers should try to {@link ModelView#close()} the returned view when it is done with, allowing any internal cleanup to occur.
     *
     * Throws if this node can't be expressed as a read-only view of the requested type.
     */
    <T> ModelView<? extends T> asReadOnly(ModelType<T> type, @Nullable ModelRuleDescriptor ruleDescriptor);

    Set<String> getLinkNames(ModelType<?> type);

    Iterable<? extends ModelNode> getLinks(ModelType<?> type);

    /**
     * Should this node be hidden from the model report.
     */
    boolean isHidden();
}
