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
import org.gradle.model.internal.core.ModelPath;

import java.util.*;

public class ModelGraph {
    private final ModelNodeData root;
    private final Map<ModelPath, ModelNodeData> flattened = Maps.newTreeMap();
    private final SetMultimap<ModelPath, ModelCreationListener> pathListeners = LinkedHashMultimap.create();
    private final SetMultimap<ModelPath, ModelCreationListener> parentListeners = LinkedHashMultimap.create();
    private final SetMultimap<ModelPath, ModelCreationListener> scopeListeners = LinkedHashMultimap.create();
    private final Set<ModelCreationListener> listeners = new LinkedHashSet<ModelCreationListener>();
    private boolean notifying;
    private final List<ModelCreationListener> pendingListeners = new ArrayList<ModelCreationListener>();
    private final List<ModelNodeData> pendingNodes = new ArrayList<ModelNodeData>();

    public ModelGraph(ModelNodeData rootNode) {
        this.root = rootNode;
        flattened.put(root.getPath(), root);
    }

    public ModelNodeData getRoot() {
        return root;
    }

    public Map<ModelPath, ModelNodeData> getFlattened() {
        return Collections.unmodifiableMap(flattened);
    }

    public void add(ModelNodeData node) {
        if (notifying) {
            pendingNodes.add(node);
            return;
        }

        doAdd(node);
        flush();
    }

    private void doAdd(ModelNodeData node) {
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

    private void notifyListeners(ModelNodeData node, Iterable<ModelCreationListener> listeners) {
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
                ModelNodeData node = flattened.get(listener.matchPath());
                if (node != null) {
                    if (maybeNotify(node, listener)) {
                        return;
                    }
                }
                pathListeners.put(listener.matchPath(), listener);
                return;
            }
            if (listener.matchParent() != null) {
                ModelNodeData parent = flattened.get(listener.matchParent());
                if (parent != null) {
                    for (ModelNodeData node : parent.getLinks().values()) {
                        if (maybeNotify(node, listener)) {
                            return;
                        }
                    }
                }
                parentListeners.put(listener.matchParent(), listener);
                return;
            }
            if (listener.matchScope() != null) {
                ModelNodeData scope = flattened.get(listener.matchScope());
                if (scope != null) {
                    if (maybeNotify(scope, listener)) {
                        return;
                    }
                    for (ModelNodeData node : scope.getLinks().values()) {
                        if (maybeNotify(node, listener)) {
                            return;
                        }
                    }
                }
                scopeListeners.put(listener.matchScope(), listener);
                return;
            }
            for (ModelNodeData node : flattened.values()) {
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

    private boolean maybeNotify(ModelNodeData node, ModelCreationListener listener) {
        if (listener.matchType() != null && !node.getPromise().canBeViewedAsWritable(listener.matchType()) && !node.getPromise().canBeViewedAsReadOnly(listener.matchType())) {
            return false;
        }
        return listener.onCreate(node);
    }

    @Nullable
    public ModelNodeData find(ModelPath path) {
        return flattened.get(path);
    }

    @Nullable
    public ModelNodeData remove(ModelPath path) {
        ModelNodeData parentNode = find(path.getParent());
        if (parentNode != null) {
            parentNode.removeLink(path.getName());
        }

        return flattened.remove(path);
    }
}
