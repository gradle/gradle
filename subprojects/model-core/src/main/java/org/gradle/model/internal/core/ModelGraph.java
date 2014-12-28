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

import com.google.common.collect.Maps;
import org.gradle.api.Nullable;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;

import java.util.*;

public class ModelGraph {
    private final ModelNode root;
    private final Map<ModelPath, ModelNode> flattened = Maps.newTreeMap();
    private final List<ModelCreationListener> listeners = new ArrayList<ModelCreationListener>();
    private boolean notifying;
    private final List<ModelCreationListener> pendingListeners = new ArrayList<ModelCreationListener>();
    private final List<ModelNode> pendingNodes = new ArrayList<ModelNode>();

    public ModelGraph() {
        EmptyModelProjection projection = new EmptyModelProjection();
        this.root = new ModelNode(ModelPath.ROOT, new SimpleModelRuleDescriptor("<root>"), projection, projection);
    }

    public ModelNode getRoot() {
        return root;
    }

    public Map<ModelPath, ModelNode> getFlattened() {
        return Collections.unmodifiableMap(flattened);
    }

    public void add(ModelNode node) {
        if (notifying) {
            pendingNodes.add(node);
            return;
        }

        doAdd(node);
        flush();
    }

    private void doAdd(ModelNode node) {
        flattened.put(node.getPath(), node);
        notifying = true;
        try {
            Iterator<ModelCreationListener> iterator = listeners.iterator();
            while (iterator.hasNext()) {
                ModelCreationListener listener = iterator.next();
                if (maybeNotify(listener, node)) {
                    iterator.remove();
                }
            }
        } finally {
            notifying = false;
        }
    }

    public void addListener(ModelCreationListener listener) {
        if (notifying) {
            pendingListeners.add(listener);
            return;
        }

        doAddListener(listener);
        flush();
    }

    private void doAddListener(ModelCreationListener listener) {
        notifying = true;
        try {
            for (ModelNode node : flattened.values()) {
                if (maybeNotify(listener, node)) {
                    return;
                }
            }
            listeners.add(listener);
        } finally {
            notifying = false;
        }
    }

    private void flush() {
        while (!pendingListeners.isEmpty()) {
            doAddListener(pendingListeners.remove(0));
        }
        while (!pendingNodes.isEmpty()) {
            doAdd(pendingNodes.remove(0));
        }
    }

    private boolean maybeNotify(ModelCreationListener listener, ModelNode node) {
        if (listener.matchPath() != null && !node.getPath().equals(listener.matchPath())) {
            return false;
        }
        if (listener.matchParent() != null && !node.getPath().getParent().equals(listener.matchParent())) {
            return false;
        }
        if (listener.matchType() != null && !node.getPromise().canBeViewedAsWritable(listener.matchType()) && !node.getPromise().canBeViewedAsReadOnly(listener.matchType())) {
            return false;
        }
        return listener.onCreate(node);
    }

    @Nullable
    public ModelNode find(ModelPath path) {
        return flattened.get(path);
    }

    @Nullable
    public ModelNode remove(ModelPath path) {
        ModelNode parentNode = find(path.getParent());
        if (parentNode != null) {
            parentNode.removeLink(path.getName());
        }

        return flattened.remove(path);
    }
}
