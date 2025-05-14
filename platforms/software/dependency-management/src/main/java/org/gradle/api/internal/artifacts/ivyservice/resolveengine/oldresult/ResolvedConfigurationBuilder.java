/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult;

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;

//builds old model of resolved dependency graph based on the result events
public interface ResolvedConfigurationBuilder {

    void addFirstLevelDependency(DependencyGraphNode dependency);

    void addChild(DependencyGraphNode parent, DependencyGraphNode child, int artifactsId);

    void addNodeArtifacts(DependencyGraphNode node, int artifactsId);

    void newResolvedDependency(DependencyGraphNode node);

    void done(DependencyGraphNode root);

}
