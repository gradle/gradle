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
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.List;
import java.util.Set;

public interface MutableModelNode extends ModelNode {

    /**
     * Creates a mutable view over this node's value.
     *
     * Callers should try to {@link ModelView#close()} the returned view when it is done with, allowing any internal cleanup to occur.
     *
     * Throws if this node can't be expressed as a mutable view of the requested type.
     */
    <T> ModelView<? extends T> asWritable(ModelType<T> type, ModelRuleDescriptor ruleDescriptor, List<ModelView<?>> implicitDependencies);

    /**
     * Adds a reference node to the graph. A reference node is a node that refers to some other node elsewhere in the graph, similar to a symbolic link.
     */
    void addReference(ModelCreator creator);

    /**
     * Adds a node to the graph, linked from this node. The given creator is used to initialize the node when required.
     */
    void addLink(ModelCreator creator);

    /**
     * Removes a node linked from this node from the graph.
     */
    void removeLink(String name);

    /**
     * Applies an action to this node.
     */
    <T> void applyToSelf(ModelActionRole type, ModelAction<T> action);

    /**
     * Applies an action to all nodes linked from this node.
     */
    <T> void applyToAllLinks(ModelActionRole type, ModelAction<T> action);

    /**
     * Applies an action to a linked node.
     */
    <T> void applyToLink(ModelActionRole type, ModelAction<T> action);

    void applyToLink(String name, Class<? extends RuleSource> rules);

    void applyToSelf(Class<? extends RuleSource> rules);

    <T> void applyToLinks(Class<T> type, Class<? extends RuleSource> rules);

    @Nullable
    MutableModelNode getLink(String name);

    int getLinkCount(ModelType<?> type);

    Set<String> getLinkNames(ModelType<?> type);

    Iterable<? extends MutableModelNode> getLinks(ModelType<?> type);

    <T> void setPrivateData(ModelType<? super T> type, T object);

    <T> T getPrivateData(ModelType<T> type);

    Object getPrivateData();

    @Nullable
    MutableModelNode getTarget();

    void setTarget(ModelNode target);

    /**
     * Ensure that the views are available, with default values applied.
     */
    void ensureUsable();

    void setHidden(boolean hidden);

    boolean isMutable();
}
