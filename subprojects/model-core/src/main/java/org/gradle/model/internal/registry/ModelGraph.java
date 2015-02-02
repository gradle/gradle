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
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.ModelPath;

import java.util.*;

public class ModelGraph {
    private final ModelNodeInternal root;
    private final Map<ModelPath, ModelNodeInternal> flattened = Maps.newTreeMap();
    private final SetMultimap<ModelPath, ModelCreationListener> pathListeners = LinkedHashMultimap.create();
    private final SetMultimap<ModelPath, ModelCreationListener> parentListeners = LinkedHashMultimap.create();
    private final SetMultimap<ModelPath, ModelCreationListener> scopeListeners = LinkedHashMultimap.create();
    private final Set<ModelCreationListener> listeners = new LinkedHashSet<ModelCreationListener>();
    private boolean notifying;
    private final List<ModelCreationListener> pendingListeners = new ArrayList<ModelCreationListener>();
    private final List<ModelNodeInternal> pendingNodes = new ArrayList<ModelNodeInternal>();

    public ModelGraph(ModelNodeInternal rootNode) {
        this.root = rootNode;
        flattened.put(root.getPath(), root);
    }

    public ModelNodeInternal getRoot() {
        return root;
    }

    public Map<ModelPath, ModelNodeInternal> getFlattened() {
        return Collections.unmodifiableMap(flattened);
    }

    public void add(ModelNodeInternal node) {
        if (notifying) {
            pendingNodes.add(node);
            return;
        }

        doAdd(node);
        flush();
    }

    private void doAdd(ModelNodeInternal node) {
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

    private void notifyListeners(ModelNodeInternal node, Iterable<ModelCreationListener> listeners) {
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
                ModelNodeInternal node = flattened.get(listener.matchPath());
                if (node != null) {
                    if (maybeNotify(node, listener)) {
                        return;
                    }
                }
                pathListeners.put(listener.matchPath(), listener);
                return;
            }
            if (listener.matchParent() != null) {
                ModelNodeInternal parent = flattened.get(listener.matchParent());
                if (parent != null) {
                    for (ModelNodeInternal node : parent.getLinks()) {
                        if (maybeNotify(node, listener)) {
                            return;
                        }
                    }
                }
                parentListeners.put(listener.matchParent(), listener);
                return;
            }
            if (listener.matchScope() != null) {
                ModelNodeInternal scope = flattened.get(listener.matchScope());
                if (scope != null) {
                    if (maybeNotify(scope, listener)) {
                        return;
                    }
                    for (ModelNodeInternal node : scope.getLinks()) {
                        if (maybeNotify(node, listener)) {
                            return;
                        }
                    }
                }
                scopeListeners.put(listener.matchScope(), listener);
                return;
            }
            for (ModelNodeInternal node : flattened.values()) {
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

    private boolean maybeNotify(ModelNodeInternal node, ModelCreationListener listener) {
        if (listener.matchType() != null && !node.getPromise().canBeViewedAsWritable(listener.matchType()) && !node.getPromise().canBeViewedAsReadOnly(listener.matchType())) {
            return false;
        }
        return listener.onCreate(node);
    }

    @Nullable
    public ModelNodeInternal find(ModelPath path) {
        return flattened.get(path);
    }

    public ModelNodeInternal get(ModelPath path) {
        ModelNodeInternal found = find(path);
        if (found == null) {
            throw new IllegalStateException("Expected model node @ '" + path + "' but none was found");
        }

        return found;
    }

    @Nullable
    public ModelNodeInternal remove(ModelNode node) {
        ModelNodeInternal parentNode = find(node.getPath().getParent());
        if (parentNode != null) {
            parentNode.removeLink(node.getPath().getName());
        }

        return flattened.remove(node.getPath());
    }
}
