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
import org.gradle.internal.Cast;
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

    public ModelReferenceNode(ModelRegistration registration, MutableModelNode parent) {
        super(registration);
        this.parent = parent;
    }

    @Override
    public void setTarget(ModelNode target) {
        if (!isMutable()) {
            throw new IllegalStateException(String.format("Cannot set target for model element '%s' as this element is not mutable.", getPath()));
        }
        this.target = (ModelNodeInternal) target;
    }

    public ModelNodeInternal getTarget() {
        return target;
    }

    @Override
    public boolean canBeViewedAs(ModelType<?> type) {
        return target != null && target.canBeViewedAs(type);
    }

    @Override
    public <T> ModelView<? extends T> asMutable(final ModelType<T> type, ModelRuleDescriptor ruleDescriptor, List<ModelView<?>> implicitDependencies) {
        if (target == null) {
            return InstanceModelView.of(getPath(), type, null);
        } else {
            return new ModelViewWrapper<T>(getPath(), target.asMutable(type, ruleDescriptor, implicitDependencies));
        }
    }

    @Override
    public <T> ModelView<? extends T> asImmutable(ModelType<T> type, @Nullable ModelRuleDescriptor ruleDescriptor) {
        if (target == null) {
            return InstanceModelView.of(getPath(), type, null);
        } else {
            return new ModelViewWrapper<T>(getPath(), target.asImmutable(type, ruleDescriptor));
        }
    }

    @Override
    public ModelPromise getPromise() {
        return target == null ? super.getPromise() : target.getPromise();
    }

    @Override
    public ModelAdapter getAdapter() {
        return target == null ? super.getAdapter() : target.getAdapter();
    }

    @Override
    public void addLink(ModelRegistration registration) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addReference(ModelRegistration registration) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeLink(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void applyToSelf(ModelActionRole type, ModelAction action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void applyToAllLinks(ModelActionRole type, ModelAction action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void applyToAllLinksTransitive(ModelActionRole type, ModelAction action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void applyToLink(ModelActionRole type, ModelAction action) {
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
    public ModelNodeInternal getLink(String name) {
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
    public void ensureAtLeast(State state) {
        if (target != null) {
            target.ensureAtLeast(state);
        }
    }

    @Override
    public MutableModelNode getParent() {
        return parent;
    }

    private static class ModelViewWrapper<T> implements ModelView<T> {
        private final ModelView<? extends T> view;
        private final ModelPath path;

        public ModelViewWrapper(ModelPath path, ModelView<? extends T> view) {
            this.path = path;
            this.view = view;
        }

        @Override
        public ModelPath getPath() {
            return path;
        }

        @Override
        public ModelType<T> getType() {
            return Cast.uncheckedCast(view.getType());
        }

        @Override
        public T getInstance() {
            return view.getInstance();
        }

        @Override
        public void close() {
            view.close();
        }
    }
}
