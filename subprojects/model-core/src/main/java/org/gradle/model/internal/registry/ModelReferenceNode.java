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

package org.gradle.model.internal.registry;

import org.gradle.api.Nullable;
import org.gradle.internal.Actions;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A model node that is a reference to some other node.
 */
class ModelReferenceNode extends ModelNodeInternal {
    private ModelNodeInternal target;
    private final MutableModelNode parent;

    public ModelReferenceNode(CreatorRuleBinder creatorBinder, MutableModelNode parent) {
        super(creatorBinder);
        this.parent = parent;
    }

    @Override
    public void setTarget(ModelNode target) {
        this.target = (ModelNodeInternal) target;
    }

    @Override
    public <T> ModelView<? extends T> asWritable(ModelType<T> type, ModelRuleDescriptor ruleDescriptor, List<ModelView<?>> implicitDependencies) {
        return target == null ? new InstanceModelView<T>(getPath(), type, null, Actions.doNothing()) : target.asWritable(type, ruleDescriptor, implicitDependencies);
    }

    @Override
    public <T> ModelView<? extends T> asReadOnly(ModelType<T> type, @Nullable ModelRuleDescriptor ruleDescriptor) {
        return target == null ? new InstanceModelView<T>(getPath(), type, null, Actions.doNothing()) : target.asReadOnly(type, ruleDescriptor);
    }

    @Override
    public ModelNodeInternal addLink(ModelNodeInternal node) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addLink(ModelCreator creator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addReference(ModelCreator creator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeLink(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> void applyToSelf(ModelActionRole type, ModelAction<T> action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> void applyToAllLinks(ModelActionRole type, ModelAction<T> action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> void applyToAllLinksTransitive(ModelActionRole type, ModelAction<T> action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> void applyToLink(ModelActionRole type, ModelAction<T> action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void applyToLink(String name, Class<? extends RuleSource> rules) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void applyToLinks(ModelType<?> type, Class<? extends RuleSource> rules) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void applyToAllLinksTransitive(ModelType<?> type, Class<? extends RuleSource> rules) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void applyToSelf(Class<? extends RuleSource> rules) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getLinkCount(ModelType<?> type) {
        return target == null ? 0 : target.getLinkCount(type);
    }

    @Override
    public Set<String> getLinkNames(ModelType<?> type) {
        return target == null ? Collections.<String>emptySet() : target.getLinkNames(type);
    }

    @Nullable
    @Override
    public MutableModelNode getLink(String name) {
        return target == null ? null : target.getLink(name);
    }

    @Override
    public Iterable<? extends ModelNodeInternal> getLinks() {
        return target == null ? Collections.<ModelNodeInternal>emptyList() : target.getLinks();
    }

    @Override
    public Iterable<? extends MutableModelNode> getLinks(ModelType<?> type) {
        return target == null ? Collections.<MutableModelNode>emptyList() : target.getLinks(type);
    }

    @Override
    public int getLinkCount() {
        return target == null ? 0 : target.getLinkCount();
    }

    @Override
    public boolean hasLink(String name, ModelType<?> type) {
        return target != null && target.hasLink(name, type);
    }

    @Override
    public boolean hasLink(String name) {
        return target != null && target.hasLink(name);
    }

    @Override
    public <T> T getPrivateData(ModelType<T> type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> void setPrivateData(Class<? super T> type, T object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> void setPrivateData(ModelType<? super T> type, T object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getPrivateData() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T getPrivateData(Class<T> type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void ensureUsable() {
        if (target != null) {
            target.ensureUsable();
        }
    }

    @Override
    public void realize() {
        throw new UnsupportedOperationException();
    }

    @Override
    public MutableModelNode getParent() {
        return parent;
    }
}
