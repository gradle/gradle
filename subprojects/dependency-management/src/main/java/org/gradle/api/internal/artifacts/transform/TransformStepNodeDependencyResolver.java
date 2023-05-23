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
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Resolves dependencies to {@link TransformStepNode} objects.
 */
@ServiceScope(Scopes.Build.class)
public class TransformStepNodeDependencyResolver implements DependencyResolver {
    @Override
    public boolean resolve(Task task, Object node, Action<? super Node> resolveAction) {
        if (node instanceof DefaultTransformNodeDependency) {
            DefaultTransformNodeDependency transformNodeDependency = (DefaultTransformNodeDependency) node;
            for (TransformStepNode transformStepNode : transformNodeDependency.getNodes()) {
                resolveAction.execute(transformStepNode);
            }
            return true;
        }
        return false;
    }
}
