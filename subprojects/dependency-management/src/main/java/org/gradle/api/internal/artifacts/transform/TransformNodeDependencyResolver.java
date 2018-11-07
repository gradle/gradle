/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.execution.plan.DependencyResolver;
import org.gradle.execution.plan.Node;

import java.util.Collection;

/**
 * Resolves dependencies to {@link TransformNode} objects.
 */
public class TransformNodeDependencyResolver implements DependencyResolver {
    private final TransformNodeFactory transformNodeFactory;

    public TransformNodeDependencyResolver(TransformNodeFactory transformNodeFactory) {
        this.transformNodeFactory = transformNodeFactory;
    }

    @Override
    public boolean resolve(Task task, Object node, Action<? super Node> resolveAction) {
        if (node instanceof DefaultArtifactTransformDependency) {
            DefaultArtifactTransformDependency transformation = (DefaultArtifactTransformDependency) node;
            Collection<TransformNode> transforms = transformNodeFactory.getOrCreate(transformation.getArtifacts(), transformation.getTransform());
            for (TransformNode transformNode : transforms) {
                resolveAction.execute(transformNode);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean attachActionTo(Task task, Action<? super Task> action) {
        return false;
    }
}
