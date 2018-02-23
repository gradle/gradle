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
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.ModelPath;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ModelGraph {
    private enum PendingState {
        ADD, NOTIFY
    }

    private final ModelNodeInternal root;
    private final Map<ModelPath, ModelNodeInternal> flattened = Maps.newTreeMap();
    private final SetMultimap<ModelPath, ModelListener> pathListeners = LinkedHashMultimap.create();
    private final SetMultimap<ModelPath, ModelListener> parentListeners = LinkedHashMultimap.create();
    private final SetMultimap<ModelPath, ModelListener> ancestorListeners = LinkedHashMultimap.create();
    private final Set<ModelListener> listeners = new LinkedHashSet<ModelListener>();
    private boolean notifying;
    private final List<ModelListener> pendingListeners = new ArrayList<ModelListener>();
    private final Map<ModelNodeInternal, PendingState> pendingNodes = Maps.newLinkedHashMap();

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
            pendingNodes.put(node, PendingState.ADD);
            return;
        }

        doAdd(node);
        flush();
    }

    public void nodeDiscovered(ModelNodeInternal node) {
        if (notifying) {
            if (!pendingNodes.containsKey(node)) {
                pendingNodes.put(node, PendingState.NOTIFY);
            }
            return;
        }

        doNotify(node);
        flush();
    }

    private void doAdd(ModelNodeInternal node) {
        flattened.put(node.getPath(), node);
        if (node.isAtLeast(ModelNode.State.Discovered)) {
            doNotify(node);
        }
    }

    private void doNotify(ModelNodeInternal node) {
        notifying = true;
        try {
            notifyListeners(node, pathListeners.get(node.getPath()));
            notifyListeners(node, parentListeners.get(node.getPath().getParent()));
            notifyListeners(node, listeners);
            if (!ancestorListeners.isEmpty()) {
                // Don't traverse path back to root when there is nothing that can possibly match
                for (ModelPath path = node.getPath().getParent(); path != null; path = path.getParent()) {
                    notifyListeners(node, ancestorListeners.get(path));
                }
            }
        } finally {
            notifying = false;
        }
    }

    private void notifyListeners(ModelNodeInternal node, Iterable<ModelListener> listeners) {
        for (ModelListener listener : listeners) {
            maybeNotify(node, listener);
        }
    }

    public void addListener(ModelListener listener) {
        if (notifying) {
            pendingListeners.add(listener);
            return;
        }

        doAddListener(listener);
        flush();
    }

    private void doAddListener(ModelListener listener) {
        notifying = true;
        try {
            if (listener.getPath() != null) {
                addPathListener(listener);
                return;
            }
            if (listener.getParent() != null) {
                addParentListener(listener);
                return;
            }
            if (listener.getAncestor() != null) {
                addAncestorListener(listener);
                return;
            }
            addEverythingListener(listener);
        } finally {
            notifying = false;
        }
    }

    private void addEverythingListener(ModelListener listener) {
        for (ModelNodeInternal node : flattened.values()) {
            maybeNotify(node, listener);
        }
        listeners.add(listener);
    }

    private void addAncestorListener(ModelListener listener) {
        if (ModelPath.ROOT.equals(listener.getAncestor())) {
            // Don't need to match on path
            addEverythingListener(listener);
            return;
        }

        ModelNodeInternal ancestor = flattened.get(listener.getAncestor());
        if (ancestor != null) {
            Deque<ModelNodeInternal> queue = new ArrayDeque<ModelNodeInternal>();
            queue.add(ancestor);
            while (!queue.isEmpty()) {
                ModelNodeInternal parent = queue.removeFirst();
                for (ModelNodeInternal node : parent.getLinks()) {
                    maybeNotify(node, listener);
                    queue.addFirst(node);
                }
            }
        }
        ancestorListeners.put(listener.getAncestor(), listener);
    }

    private void addParentListener(ModelListener listener) {
        ModelNodeInternal parent = flattened.get(listener.getParent());
        if (parent != null) {
            for (ModelNodeInternal node : parent.getLinks()) {
                maybeNotify(node, listener);
            }
        }
        parentListeners.put(listener.getParent(), listener);
    }

    private void addPathListener(ModelListener listener) {
        ModelNodeInternal node = flattened.get(listener.getPath());
        if (node != null) {
            maybeNotify(node, listener);
        }
        pathListeners.put(listener.getPath(), listener);
    }

    private void flush() {
        while (!pendingListeners.isEmpty()) {
            doAddListener(pendingListeners.remove(0));
        }
        while (!pendingNodes.isEmpty()) {
            Iterator<Map.Entry<ModelNodeInternal, PendingState>> iPendingNodes = pendingNodes.entrySet().iterator();
            Map.Entry<ModelNodeInternal, PendingState> entry = iPendingNodes.next();
            iPendingNodes.remove();
            ModelNodeInternal pendingNode = entry.getKey();
            switch (entry.getValue()) {
                case ADD:
                    doAdd(pendingNode);
                    break;
                case NOTIFY:
                    doNotify(pendingNode);
                    break;
            }
        }
    }

    private void maybeNotify(ModelNodeInternal node, ModelListener listener) {
        if (!node.isAtLeast(ModelNode.State.Discovered)) {
            return;
        }
        listener.onDiscovered(node);
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
