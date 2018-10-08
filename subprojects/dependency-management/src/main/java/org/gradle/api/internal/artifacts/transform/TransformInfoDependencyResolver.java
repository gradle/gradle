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
import org.gradle.execution.taskgraph.WorkInfo;
import org.gradle.execution.taskgraph.WorkInfoDependencyResolver;

import java.util.Collection;

/**
 * Resolves dependencies to {@link TransformInfo} objects.
 */
public class TransformInfoDependencyResolver implements WorkInfoDependencyResolver {
    private final TransformInfoFactory transformInfoFactory;

    public TransformInfoDependencyResolver(TransformInfoFactory transformInfoFactory) {
        this.transformInfoFactory = transformInfoFactory;
    }

    @Override
    public boolean resolve(Task task, Object node, Action<? super WorkInfo> resolveAction) {
        if (node instanceof DefaultArtifactTransformDependency) {
            DefaultArtifactTransformDependency transformation = (DefaultArtifactTransformDependency) node;
            Collection<TransformInfo> transforms = transformInfoFactory.getOrCreate(transformation.getArtifacts(), transformation.getTransform());
            for (TransformInfo transformInfo : transforms) {
                resolveAction.execute(transformInfo);
            }
            return true;
        }
        return false;
    }
}
