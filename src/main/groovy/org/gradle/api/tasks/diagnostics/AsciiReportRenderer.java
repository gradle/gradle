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

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.util.GUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;

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

    public void render(ResolvedConfiguration resolvedConfiguration) throws IOException
    {
        Set<ResolvedDependency> mergedRoots = mergeChildren(resolvedConfiguration.getFirstLevelResolvedDependencies());
        for (ResolvedDependency root : mergedRoots) {
            render(root, 1);
        }
    }

    private void render(ResolvedDependency resolvedDependency, int depth) throws IOException
    {
        getFormatter().format(getIndent(depth));
		getFormatter().format("%s:%s%n", resolvedDependency.getName(), resolvedDependency.getConfiguration());

        Collection<ResolvedDependency> mergedChildren = mergeChildren(resolvedDependency.getChildren());

		for(ResolvedDependency childResolvedDependency : mergedChildren)
		{
			render(childResolvedDependency, depth + 1);
		}
    }

    private Set<ResolvedDependency> mergeChildren(Set<ResolvedDependency> children) {
        Map<String, Set<ResolvedDependency>> mergedGroups = new HashMap<String, Set<ResolvedDependency>>();
        for (ResolvedDependency child : children) {
            Set<ResolvedDependency> mergeGroup = mergedGroups.get(child.getName());
            if (mergeGroup == null) {
                mergedGroups.put(child.getName(), mergeGroup = new HashSet<ResolvedDependency>());
            }
            mergeGroup.add(child);
        }
        Set<ResolvedDependency> mergedChildren = new HashSet<ResolvedDependency>();
        for (Set<ResolvedDependency> mergedGroup : mergedGroups.values()) {
            mergedChildren.add(new MergedResolvedDependency(mergedGroup));
        }
        return mergedChildren;
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

    private static class MergedResolvedDependency implements ResolvedDependency {
        private Set<ResolvedDependency> mergedResolvedDependencies = new LinkedHashSet<ResolvedDependency>();

        public MergedResolvedDependency(Set<ResolvedDependency> mergedResolvedDependencies) {
            assert !mergedResolvedDependencies.isEmpty();
            this.mergedResolvedDependencies = mergedResolvedDependencies;
        }

        public String getName() {
            return mergedResolvedDependencies.iterator().next().getName();
        }

        public String getConfiguration() {
            String mergedConfiguration = "";
            for (ResolvedDependency mergedResolvedDependency : mergedResolvedDependencies) {
                mergedConfiguration += mergedResolvedDependency.getConfiguration() + ",";
            }
            return mergedConfiguration.substring(0, mergedConfiguration.length() - 1);
        }

        public Set<ResolvedDependency> getChildren() {
            Set<ResolvedDependency> mergedChildren = new LinkedHashSet<ResolvedDependency>();
            for (ResolvedDependency mergedResolvedDependency : mergedResolvedDependencies) {
                mergedChildren.addAll(mergedResolvedDependency.getChildren());
            }
            return mergedChildren;
        }

        public Set<ResolvedDependency> getParents() {
            throw new UnsupportedOperationException();
        }

        public Set<File> getFiles() {
            Set<File> mergedFiles = new LinkedHashSet<File>();
            for (ResolvedDependency mergedResolvedDependency : mergedResolvedDependencies) {
                mergedFiles.addAll(mergedResolvedDependency.getFiles());
            }
            return mergedFiles;
        }

        public Set<File> getAllFiles() {
            throw new UnsupportedOperationException();
        }
    }
}
