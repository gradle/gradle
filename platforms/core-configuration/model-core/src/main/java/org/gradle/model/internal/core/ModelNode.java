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

import com.google.common.base.Optional;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

public interface ModelNode {

    boolean hasLink(String name);

    boolean hasLink(String name, ModelType<?> type);

    // Note: order is crucial here. Nodes are traversed through these states in the order defined below
    enum State {
        Registered(true), // Initial state. Only path and some projections are known here
        Discovered(true), // All projections are defined
        Created(true), // Private data has been created, initial rules discovered
        DefaultsApplied(true), // Default values have been applied
        Initialized(true),
        Mutated(true),
        Finalized(false),
        SelfClosed(false),
        GraphClosed(false);

        public final boolean mutable;

        State(boolean mutable) {
            this.mutable = mutable;
        }

        public State previous() {
            return ModelNode.State.values()[ordinal() - 1];
        }

        public boolean isAtLeast(State state) {
            return this.ordinal() >= state.ordinal();
        }
    }

    ModelPath getPath();

    ModelRuleDescriptor getDescriptor();

    State getState();

    /**
     * Creates an immutable view over this node's value.
     *
     * Callers should try to {@link ModelView#close()} the returned view when it is done with, allowing any internal cleanup to occur.
     *
     * Throws if this node can't be expressed as an immutable view of the requested type.
     */
    <T> ModelView<? extends T> asImmutable(ModelType<T> type, @Nullable ModelRuleDescriptor ruleDescriptor);

    Set<String> getLinkNames(ModelType<?> type);

    Iterable<? extends ModelNode> getLinks(ModelType<?> type);

    /**
     * Should this node be hidden from the model report.
     */
    boolean isHidden();

    /**
     * The number of link this node has.
     */
    int getLinkCount();

    /**
     * Gets the value represented by this node.
     *
     * Calling this method may create or transition the node.
     */
    Optional<String> getValueDescription();

    /**
     * Gets the underlying type of this node.
     * <p>
     * Calling this method may create or transition the node.
     * <p>
     * In practice, this describes the type that you would get if you asked for this node as Object, read only.
     * This is used in the model report.
     * In the future we may need a more sophisticated (e.g. multi-type aware, visibility aware) mechanism for advertising the type.
     * <p>
     * If an absent is returned, this node can not be viewed as an object.
     */
    Optional<String> getTypeDescription();

    /**
     * Gets the rules that have been executed on this node in the order in which they were executed.
     */
    List<ModelRuleDescriptor> getExecutedRules();
}
