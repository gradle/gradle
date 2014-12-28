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

import com.google.common.collect.Maps;
import org.gradle.api.Nullable;
import org.gradle.internal.Cast;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.Map;

public class ModelNode implements ModelCreation {
    public enum State {
        Known(true),
        Created(true),
        DefaultsApplied(true),
        Initialized(true),
        Mutated(true),
        Finalized(false),
        SelfClosed(false),
        GraphClosed(false);

        final boolean mutable;

        State(boolean mutable) {
            this.mutable = mutable;
        }
    }
    private final ModelPath creationPath;
    private final ModelRuleDescriptor descriptor;
    private final ModelPromise promise;
    private final ModelAdapter adapter;

    private final Map<String, ModelNode> links = Maps.newTreeMap();
    private Object privateData;
    private ModelType<?> privateDataType;
    private State state = State.Known;

    public ModelNode(ModelPath creationPath, ModelRuleDescriptor descriptor, ModelPromise promise, ModelAdapter adapter) {
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

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public boolean isMutable() {
        return state.mutable;
    }

    public boolean canApply(ModelActionRole type) {
        return type.ordinal() > state.ordinal() - State.Created.ordinal();
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
    public ModelNode getLink(String name) {
        return links.get(name);
    }

    public ModelNode addLink(String name, ModelRuleDescriptor descriptor, ModelPromise promise, ModelAdapter adapter) {
        ModelNode node = new ModelNode(creationPath.child(name), descriptor, promise, adapter);
        links.put(name, node);
        return node;
    }

    public Map<String, ModelNode> getLinks() {
        return Collections.unmodifiableMap(links);
    }

    @Nullable
    public ModelNode removeLink(String name) {
        return links.remove(name);
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
