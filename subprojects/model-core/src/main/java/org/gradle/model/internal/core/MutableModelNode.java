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

import java.util.Set;

public interface MutableModelNode extends ModelNode {
    boolean canBeViewedAs(ModelType<?> type);

    /**
     * Creates a mutable view over this node's value.
     *
     * Callers should try to {@link ModelView#close()} the returned view when it is done with, allowing any internal cleanup to occur.
     *
     * Throws if this node can't be expressed as a mutable view of the requested type.
     */
    <T> ModelView<? extends T> asMutable(ModelType<T> type, ModelRuleDescriptor ruleDescriptor);

    /**
     * Adds a reference node to the graph. A reference node is a node that refers to some other node elsewhere in the graph, similar to a symbolic link.
     *
     * The path returned by {@link ModelRegistration#getPath()} is used to determine the name of the reference.
     */
    void addReference(ModelRegistration registration);

    /**
     * Adds a node to the graph, linked from this node. The given registration is used to initialize the node when required.
     *
     * The path returned by {@link ModelRegistration#getPath()} is used to determine the name of the link.
     */
    void addLink(ModelRegistration registration);

    /**
     * Removes a node linked from this node from the graph.
     */
    void removeLink(String name);

    /**
     * Applies an action to this node.
     */
    void applyToSelf(ModelActionRole type, ModelAction action);

    /**
     * Applies an action to all nodes linked from this node.
     *
     * The type returned by {@link ModelAction#getSubject()} is used to filter the nodes, such that the action is applied only to those linked nodes with a view of the
     * requested type available.
     */
    void applyToAllLinks(ModelActionRole type, ModelAction action);

    /**
     * Applies an action to all nodes linked from this node, including all nodes transitively linked from this node.
     *
     * The type returned by {@link ModelAction#getSubject()} is used to filter the nodes, such that the action is applied only to those linked nodes with a view of the
     * requested type available.
     */
    void applyToAllLinksTransitive(ModelActionRole type, ModelAction action);

    /**
     * Applies an action to a linked node.
     *
     * The path returned by {@link ModelAction#getSubject()} is used to select the link to apply the action to.
     */
    void applyToLink(ModelActionRole type, ModelAction action);

    /**
     * Applies the given rules to a node linked from this node.
     */
    void applyToLink(String name, Class<? extends RuleSource> rules);

    /**
     * Applies the given rules to this node.
     */
    void applyToSelf(Class<? extends RuleSource> rules);

    /**
     * Applies the given rules to all nodes of the given type linked from this node.
     */
    void applyToLinks(ModelType<?> type, Class<? extends RuleSource> rules);

    /**
     * Applies the given rules to all nodes of the given type transitively linked from this node.
     */
    void applyToAllLinksTransitive(ModelType<?> type, Class<? extends RuleSource> rules);

    @Nullable
    MutableModelNode getLink(String name);

    int getLinkCount(ModelType<?> type);

    Set<String> getLinkNames(ModelType<?> type);

    Iterable<? extends MutableModelNode> getLinks(ModelType<?> type);

    <T> void setPrivateData(Class<? super T> type, T object);

    <T> void setPrivateData(ModelType<? super T> type, T object);

    <T> T getPrivateData(Class<T> type);

    <T> T getPrivateData(ModelType<T> type);

    Object getPrivateData();

    void setTarget(ModelNode target);

    /**
     * Ensure that the views are available, with default values applied.
     */
    void ensureUsable();

    void ensureAtLeast(ModelNode.State state);

    boolean isAtLeast(ModelNode.State state);

    void setHidden(boolean hidden);

    boolean isMutable();

    MutableModelNode getParent();

    void addProjection(ModelProjection projection);
}
