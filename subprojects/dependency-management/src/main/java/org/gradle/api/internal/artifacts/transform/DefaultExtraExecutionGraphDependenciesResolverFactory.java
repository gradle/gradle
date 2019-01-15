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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.tasks.WorkNodeAction;
import org.gradle.internal.Factory;

public class DefaultExtraExecutionGraphDependenciesResolverFactory implements ExtraExecutionGraphDependenciesResolverFactory {
    private final Factory<ResolverResults> graphResults;
    private final Factory<ResolverResults> artifactResults;
    private final WorkNodeAction graphResolveAction;

    public DefaultExtraExecutionGraphDependenciesResolverFactory(Factory<ResolverResults> graphResults, Factory<ResolverResults> artifactResults, WorkNodeAction graphResolveAction) {
        this.graphResults = graphResults;
        this.artifactResults = artifactResults;
        this.graphResolveAction = graphResolveAction;
    }

    @Override
    public ExecutionGraphDependenciesResolver create(ComponentIdentifier componentIdentifier) {
        return new DefaultExecutionGraphDependenciesResolver(componentIdentifier, graphResults, artifactResults, graphResolveAction);
    }
}
