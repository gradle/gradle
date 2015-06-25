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

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import org.gradle.api.Nullable;
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.ModelPath;

import java.util.*;

class ModelGraph {
    private final ModelNodeInternal root;
    private final Map<ModelPath, ModelNodeInternal> flattened = Maps.newTreeMap();
    private final SetMultimap<ModelPath, ModelCreationListener> pathListeners = LinkedHashMultimap.create();
    private final SetMultimap<ModelPath, ModelCreationListener> parentListeners = LinkedHashMultimap.create();
    private final SetMultimap<ModelPath, ModelCreationListener> ancestorListeners = LinkedHashMultimap.create();
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

    private void addEverythingListener(ModelCreationListener listener) {
        for (ModelNodeInternal node : flattened.values()) {
            if (maybeNotify(node, listener)) {
                return;
            }
        }
        listeners.add(listener);
    }

    private void addAncestorListener(ModelCreationListener listener) {
        if (listener.getAncestor().equals(ModelPath.ROOT)) {
            // Don't need to match on path
            addEverythingListener(listener);
            return;
        }

        ModelNodeInternal ancestor = flattened.get(listener.getAncestor());
        if (ancestor != null) {
            LinkedList<ModelNodeInternal> queue = new LinkedList<ModelNodeInternal>();
            queue.add(ancestor);
            while (!queue.isEmpty()) {
                ModelNodeInternal parent = queue.removeFirst();
                for (ModelNodeInternal node : parent.getLinks()) {
                    if (maybeNotify(node, listener)) {
                        return;
                    }
                    queue.addFirst(node);
                }
            }
        }
        ancestorListeners.put(listener.getAncestor(), listener);
    }

    private void addParentListener(ModelCreationListener listener) {
        ModelNodeInternal parent = flattened.get(listener.getParent());
        if (parent != null) {
            for (ModelNodeInternal node : parent.getLinks()) {
                if (maybeNotify(node, listener)) {
                    return;
                }
            }
        }
        parentListeners.put(listener.getParent(), listener);
    }

    private void addPathListener(ModelCreationListener listener) {
        ModelNodeInternal node = flattened.get(listener.getPath());
        if (node != null) {
            if (maybeNotify(node, listener)) {
                return;
            }
        }
        pathListeners.put(listener.getPath(), listener);
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
        if (listener.getType() != null && !node.getPromise().canBeViewedAsWritable(listener.getType()) && !node.getPromise().canBeViewedAsReadOnly(listener.getType())) {
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

    public Iterable<ModelNodeInternal> findAllInScope(ModelPath scope) {
        ModelNodeInternal node = flattened.get(scope);
        if (node == null) {
            return Collections.emptyList();
        }
        return Iterables.concat(Collections.singleton(node), node.getLinks());
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
