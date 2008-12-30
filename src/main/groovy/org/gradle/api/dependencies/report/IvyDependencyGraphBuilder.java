package org.gradle.api.dependencies.report;

import org.gradle.api.Project;
import org.apache.ivy.Ivy;
import org.apache.ivy.core.cache.ResolutionCacheManager;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.core.resolve.IvyNodeCallers;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.report.ResolveReport;
import org.dom4j.Document;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.io.File;

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
     * @return
     * @throws Exception
     */
    public IvyDependencyGraph buildGraph(Project project, ResolveReport report, String conf) throws Exception
    {
		IvyDependencyGraph graph = new IvyDependencyGraph();

        List<IvyNode> dependencies = report.getDependencies();

        IvyDependency root = graph.findOrCreateDependeny(project.property("group").toString(), project.getName(), project.property("version").toString());

        graph.setRoot(root);

        for(IvyNode dependency : dependencies)
        {
            root.addDependency(processNode(dependency, graph, conf));
        }

        return graph;
    }
}
