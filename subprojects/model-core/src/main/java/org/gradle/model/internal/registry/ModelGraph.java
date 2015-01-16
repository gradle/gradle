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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import org.gradle.api.Nullable;
import org.gradle.model.internal.core.EmptyModelProjection;
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;

import java.util.*;

public class ModelGraph {
    private final ModelNode root;
    private final Map<ModelPath, ModelNode> flattened = Maps.newTreeMap();
    private final SetMultimap<ModelPath, ModelCreationListener> pathListeners = LinkedHashMultimap.create();
    private final SetMultimap<ModelPath, ModelCreationListener> parentListeners = LinkedHashMultimap.create();
    private final SetMultimap<ModelPath, ModelCreationListener> scopeListeners = LinkedHashMultimap.create();
    private final Set<ModelCreationListener> listeners = new LinkedHashSet<ModelCreationListener>();
    private boolean notifying;
    private final List<ModelCreationListener> pendingListeners = new ArrayList<ModelCreationListener>();
    private final List<ModelNode> pendingNodes = new ArrayList<ModelNode>();

    public ModelGraph() {
        EmptyModelProjection projection = new EmptyModelProjection();
        root = new ModelNode(ModelPath.ROOT, new SimpleModelRuleDescriptor("<root>"), projection, projection);
        flattened.put(root.getPath(), root);
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
            notifyListeners(node, pathListeners.get(node.getPath()));
            notifyListeners(node, parentListeners.get(node.getPath().getParent()));
            notifyListeners(node, scopeListeners.get(node.getPath()));
            notifyListeners(node, scopeListeners.get(node.getPath().getParent()));
            notifyListeners(node, listeners);
        } finally {
            notifying = false;
        }
    }

    private void notifyListeners(ModelNode node, Iterable<ModelCreationListener> listeners) {
        Iterator<ModelCreationListener> iterator = listeners.iterator();
        while (iterator.hasNext()) {
            ModelCreationListener listener = iterator.next();
            if (maybeNotify(node, listener)) {
                iterator.remove();
            }
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
            if (listener.matchPath() != null) {
                ModelNode node = flattened.get(listener.matchPath());
                if (node != null) {
                    if (maybeNotify(node, listener)) {
                        return;
                    }
                }
                pathListeners.put(listener.matchPath(), listener);
                return;
            }
            if (listener.matchParent() != null) {
                ModelNode parent = flattened.get(listener.matchParent());
                if (parent != null) {
                    for (ModelNode node : parent.getLinks().values()) {
                        if (maybeNotify(node, listener)) {
                            return;
                        }
                    }
                }
                parentListeners.put(listener.matchParent(), listener);
                return;
            }
            if (listener.matchScope() != null) {
                ModelNode scope = flattened.get(listener.matchScope());
                if (scope != null) {
                    if (maybeNotify(scope, listener)) {
                        return;
                    }
                    for (ModelNode node : scope.getLinks().values()) {
                        if (maybeNotify(node, listener)) {
                            return;
                        }
                    }
                }
                scopeListeners.put(listener.matchScope(), listener);
                return;
            }
            for (ModelNode node : flattened.values()) {
                if (maybeNotify(node, listener)) {
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

    private boolean maybeNotify(ModelNode node, ModelCreationListener listener) {
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
