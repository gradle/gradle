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
package org.gradle.api.tasks.diagnostics;

import org.gradle.api.dependencies.report.IvyDependencyGraph;
import org.gradle.api.dependencies.report.IvyDependency;

import java.io.OutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Set;
import java.util.HashSet;

/**
 * DependencyGraphRenderer that emits simple graphviz dot notation for a dependency
 * tree.
 *
 * @author Phil Messenger
 */
public class GraphvizGraphRenderer implements DependencyGraphRenderer
{
    public void render(IvyDependencyGraph graph, OutputStream output) throws IOException
    {
        OutputStreamWriter writer = new OutputStreamWriter(output);

        writer.write("digraph " + graph.getConf() + " {\n");

		Set<String> edges = new HashSet<String>();

		buildDotDependencyTree(graph.getRoot(), edges);

		for(String edge : edges)
		{
			writer.write(edge + "\n");
		}

		writer.write("}\n" );
    }

    /**
     * @todo - need to check name escaping?
     * 
     * @param root
     * @param edges
     */
    private void buildDotDependencyTree(IvyDependency root, Set<String> edges)
	{
		for(IvyDependency dep : root.getDependencies())
		{
			String edge = "\"" + root.getName() + "\" -> \"" + dep.getName().replace('-', '_') + "\";";
			edges.add(edge);
		}

		for(IvyDependency dep : root.getDependencies())
		{
			buildDotDependencyTree(dep, edges);
		}
	}
}
