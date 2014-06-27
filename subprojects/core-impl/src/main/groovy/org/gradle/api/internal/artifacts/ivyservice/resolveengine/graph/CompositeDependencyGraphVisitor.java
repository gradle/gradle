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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph;

import java.util.Arrays;
import java.util.List;

class CompositeDependencyGraphVisitor implements DependencyGraphVisitor {
    private final List<DependencyGraphVisitor> visitors;

    CompositeDependencyGraphVisitor(DependencyGraphVisitor... visitors) {
        this.visitors = Arrays.asList(visitors);
    }

    public void start(DependencyGraphBuilder.ConfigurationNode root) {
        for (DependencyGraphVisitor visitor : visitors) {
            visitor.start(root);
        }
    }

    public void visitNode(DependencyGraphBuilder.ConfigurationNode resolvedConfiguration) {
        for (DependencyGraphVisitor visitor : visitors) {
            visitor.visitNode(resolvedConfiguration);
        }
    }

    public void visitEdge(DependencyGraphBuilder.ConfigurationNode resolvedConfiguration) {
        for (DependencyGraphVisitor visitor : visitors) {
            visitor.visitEdge(resolvedConfiguration);
        }
    }

    public void finish(DependencyGraphBuilder.ConfigurationNode root) {
        for (DependencyGraphVisitor visitor : visitors) {
            visitor.finish(root);
        }
    }
}
