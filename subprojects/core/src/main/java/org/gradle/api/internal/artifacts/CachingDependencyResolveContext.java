/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.internal.graph.CachingDirectedGraphWalker;
import org.gradle.internal.graph.DirectedGraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CachingDependencyResolveContext implements DependencyResolveContext {
    private final List<Object> queue = new ArrayList<Object>();
    private final CachingDirectedGraphWalker<Object, FileCollectionInternal> walker = new CachingDirectedGraphWalker<Object, FileCollectionInternal>(new DependencyGraph());
    private final TaskDependencyFactory taskDependencyFactory;
    private final boolean transitive;
    private final Map<String, String> attributes;

    public CachingDependencyResolveContext(TaskDependencyFactory taskDependencyFactory, boolean transitive, Map<String, String> attributes) {
        this.taskDependencyFactory = taskDependencyFactory;
        this.transitive = transitive;
        this.attributes = attributes;
    }

    @Override
    public boolean isTransitive() {
        return transitive;
    }

    @Override
    public Map<String, String> getAttributes() {
        return attributes;
    }

    public FileCollection resolve() {
        try {
            walker.add(queue);
            return new UnionFileCollection(taskDependencyFactory, walker.findValues());
        } finally {
            queue.clear();
        }
    }

    @Override
    public void add(Object dependency) {
        queue.add(dependency);
    }

    private class DependencyGraph implements DirectedGraph<Object, FileCollectionInternal> {
        @Override
        public void getNodeValues(Object node, Collection<? super FileCollectionInternal> values, Collection<? super Object> connectedNodes) {
            if (node instanceof FileCollectionInternal) {
                FileCollectionInternal fileCollection = (FileCollectionInternal) node;
                values.add(fileCollection);
            } else if (node instanceof ResolvableDependency) {
                ResolvableDependency resolvableDependency = (ResolvableDependency) node;
                queue.clear();
                resolvableDependency.resolve(CachingDependencyResolveContext.this);
                connectedNodes.addAll(queue);
                queue.clear();
            } else {
                throw new IllegalArgumentException(String.format("Cannot resolve object of unknown type %s.", node.getClass().getSimpleName()));
            }
        }
    }
}
