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

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.report.IvyDependency;
import org.gradle.api.artifacts.report.IvyDependencyGraph;
import org.gradle.api.Project;
import org.gradle.util.GUtil;

import java.io.IOException;

/**
 * Simple dependency graph renderer that emits an ASCII tree.
 *
 * @author Phil Messenger
 */
public class AsciiReportRenderer extends TextProjectReportRenderer implements DependencyReportRenderer {
    private boolean hasConfigs;

    public AsciiReportRenderer() {
    }

    public AsciiReportRenderer(Appendable writer) {
        super(writer);
    }

    @Override
    public void startProject(Project project) {
        super.startProject(project);
        hasConfigs = false;
    }

    @Override
    public void completeProject(Project project) {
        if (!hasConfigs) {
            getFormatter().format("No configurations%n");
        }
        super.completeProject(project);
    }

    public void startConfiguration(Configuration configuration) {
        hasConfigs = true;
        getFormatter().format("%s%s%n", configuration.getName(), getDescription(configuration));
    }

    private String getDescription(Configuration configuration) {
        return GUtil.isTrue(configuration.getDescription()) ? " - " + configuration.getDescription() : "";
    }

    public void completeConfiguration(Configuration configuration) {
    }

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

			buffer.append("      ");
		}

		buffer.append("|-----");

		return buffer.toString();
	}
}
