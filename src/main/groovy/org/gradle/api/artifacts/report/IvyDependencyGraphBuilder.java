/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.api.artifacts.report;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.IvyNode;

import java.util.Collection;
import java.util.List;

/**
 * Builds a collection of IvyDependencyGraphs for a given project.
 *
 * @author Phil Messenger
 */
public class IvyDependencyGraphBuilder
{

    private IvyDependency processNode(IvyNode node, IvyDependencyGraph graph, String conf)
    {
        String name = node.getResolvedId().getName();
        String group = node.getResolvedId().getOrganisation();
        String revision = node.getResolvedId().getRevision();

        IvyDependency ivyDependency = graph.findOrCreateDependeny(name, group, revision);

        Collection<IvyNode> dependencies = node.getDependencies(conf, new String[] { conf });

        for(IvyNode dependency : dependencies)
        {
            IvyDependency retDep = processNode(dependency, graph, conf);
            
            ivyDependency.addDependency(retDep);
        }

        return ivyDependency;
    }

    /**
     * This method builds a collection of Graphs for each Ivy dependency (xml) report.
     * @throws Exception
     * @return The Ivy dependency graph
     */
    public IvyDependencyGraph buildGraph(ResolveReport report, String conf)
    {
		IvyDependencyGraph graph = new IvyDependencyGraph();

        List<IvyNode> dependencies = report.getDependencies();

        ModuleRevisionId revisionId = report.getModuleDescriptor().getModuleRevisionId();
        IvyDependency root = graph.findOrCreateDependeny(revisionId.getOrganisation(), revisionId.getName(), revisionId.getRevision());

        graph.setRoot(root);

        for(IvyNode dependency : dependencies)
        {
            root.addDependency(processNode(dependency, graph, conf));
        }

        return graph;
    }
}
