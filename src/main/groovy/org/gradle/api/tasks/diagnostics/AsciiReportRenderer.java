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

import java.io.*;

/**
 * Simple dependency graph renderer that emits an ASCII tree.
 *
 * @author Phil Messenger
 */
public class AsciiReportRenderer extends TextProjectReportRenderer implements DependencyReportRenderer {

    public void render(IvyDependencyGraph graph) throws IOException
    {
        render(graph.getRoot(), 1);
    }

    private void render(IvyDependency node, int depth) throws IOException
    {
        getFormatter().format(getIndent(depth));
		getFormatter().format("%s%n", node);

		for(IvyDependency dep : node.getDependencies())
		{
			render(dep, depth + 1);
		}
    }

	private String getIndent(int depth)
	{
		StringBuilder buffer = new StringBuilder();

		for(int x = 0; x < depth - 1; x++)
		{
            if(x > 0)
            {
                buffer.append("|");
            }

			buffer.append("\t");
		}

		buffer.append("|-----");

		return buffer.toString();
	}
}
