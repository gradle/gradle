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
import org.gradle.internal.graph.CachingDirectedGraphWalker;
import org.gradle.internal.graph.DirectedGraph;
import org.gradle.api.internal.file.UnionFileCollection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CachingDependencyResolveContext implements DependencyResolveContext {
    private final List<Object> queue = new ArrayList<Object>();
    private final CachingDirectedGraphWalker<Object, FileCollection> walker = new CachingDirectedGraphWalker<Object, FileCollection>(new DependencyGraph());
    private final boolean transitive;

    public CachingDependencyResolveContext(boolean transitive) {
        this.transitive = transitive;
    }

    public boolean isTransitive() {
        return transitive;
    }

    public FileCollection resolve() {
        try {
            walker.add(queue);
            return new UnionFileCollection(walker.findValues());
        } finally {
            queue.clear();
        }
    }

    public void add(Object dependency) {
        queue.add(dependency);
    }

    private class DependencyGraph implements DirectedGraph<Object, FileCollection> {
        public void getNodeValues(Object node, Collection<? super FileCollection> values, Collection<? super Object> connectedNodes) {
            if (node instanceof FileCollection) {
                FileCollection fileCollection = (FileCollection) node;
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
