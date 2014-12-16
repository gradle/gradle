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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ModelGraph {

    private final Map<String, ModelNode> entryNodes = Maps.newTreeMap();
    private final Map<ModelPath, ModelNode> flattened = Maps.newTreeMap();
    private final Action<? super ModelNode> onAdd;

    public ModelGraph(Action<? super ModelNode> onAdd) {
        this.onAdd = onAdd;
    }

    public ModelNode addEntryPoint(String name, ModelRuleDescriptor descriptor, ModelPromise promise, ModelAdapter adapter) {

        // Disabled before 2.3 release due to not wanting to validate task names (which may contain invalid chars), at least not yet
        // ModelPath.validateName(name);

        ModelNode node = new ModelNode(this, ModelPath.path(name), descriptor, promise, adapter);
        ModelNode previous = entryNodes.put(name, node);
        if (previous != null) {
            // TODO more context here
            throw new IllegalStateException("attempt to replace node link: " + name);
        }

        flattened.put(ModelPath.path(name), node);
        return node;
    }

    public Map<ModelPath, ModelNode> getFlattened() {
        return Collections.unmodifiableMap(flattened);
    }

    public ModelSearchResult search(ModelPath path) {
        List<String> reached = Lists.newArrayListWithCapacity(path.getDepth());
        ModelNode node = null;
        ModelNode nextNode;
        for (String pathComponent : path) {
            if (node == null) {
                nextNode = entryNodes.get(pathComponent);
            } else {
                nextNode = node.getLinks().get(pathComponent);
            }

            if (nextNode == null) {
                if (reached.isEmpty()) {
                    return new ModelSearchResult(null, path, null, null);
                } else {
                    return new ModelSearchResult(null, path, node, new ModelPath(reached));
                }
            } else {
                node = nextNode;
            }
        }

        return new ModelSearchResult(node, path, node, path);
    }

    void onNewChildNode(ModelNode child) {
        flattened.put(child.getCreationPath(), child);
        onAdd.execute(child);
    }

    @Nullable
    public ModelNode remove(ModelPath path) {
        if (path.isTopLevel()) {
            entryNodes.remove(path.getName());
        } else {
            ModelSearchResult searchResult = search(path.getParent());
            if (searchResult.getTargetNode() != null) {
                searchResult.getTargetNode().removeLink(path.getName());
            }

        }

        return flattened.remove(path);
    }
}
