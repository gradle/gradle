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

package org.gradle.model.internal.registry;

import com.google.common.collect.Maps;
import org.gradle.api.Nullable;
import org.gradle.internal.Cast;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.Map;

abstract class ModelNodeInternal implements MutableModelNode {
    private final ModelPath creationPath;
    private final ModelRuleDescriptor descriptor;
    private final ModelPromise promise;
    private final ModelAdapter adapter;

    private final Map<String, ModelNodeInternal> links = Maps.newTreeMap();
    private Object privateData;
    private ModelType<?> privateDataType;
    private ModelNode.State state = ModelNode.State.Known;

    public ModelNodeInternal(ModelPath creationPath, ModelRuleDescriptor descriptor, ModelPromise promise, ModelAdapter adapter) {
        this.creationPath = creationPath;
        this.descriptor = descriptor;
        this.promise = promise;
        this.adapter = adapter;
    }

    public ModelPath getPath() {
        return creationPath;
    }

    public ModelRuleDescriptor getDescriptor() {
        return descriptor;
    }

    public ModelNode.State getState() {
        return state;
    }

    public void setState(ModelNode.State state) {
        this.state = state;
    }

    public boolean isMutable() {
        return state.mutable;
    }

    public boolean canApply(ModelActionRole type) {
        return type.ordinal() >= state.ordinal() - ModelNode.State.Created.ordinal();
    }

    public ModelPromise getPromise() {
        return promise;
    }

    public ModelAdapter getAdapter() {
        return adapter;
    }

    @Override
    public String toString() {
        return creationPath.toString();
    }

    public boolean hasLink(String name) {
        return links.containsKey(name);
    }

    @Nullable
    public ModelNodeInternal getLink(String name) {
        return links.get(name);
    }

    public ModelNodeInternal addLink(ModelNodeInternal node) {
        links.put(node.getPath().getName(), node);
        return node;
    }

    public Map<String, ModelNodeInternal> getLinks() {
        return Collections.unmodifiableMap(links);
    }

    public void removeLink(String name) {
        links.remove(name);
    }

    public <T> T getPrivateData(ModelType<T> type) {
        if (privateData == null) {
            return null;
        }

        if (!type.isAssignableFrom(privateDataType)) {
            throw new ClassCastException("Cannot get private data '" + privateData + "' of type '" + privateDataType + "' as type '" + type);
        }
        return Cast.uncheckedCast(privateData);
    }

    public <T> void setPrivateData(ModelType<T> type, T object) {
        this.privateDataType = type;
        this.privateData = object;
    }
}
