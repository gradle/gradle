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
package org.gradle.api.tasks.diagnostics.internal;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.util.GUtil;

import java.io.IOException;
import java.util.*;

import static org.gradle.logging.StyledTextOutput.Style.*;

/**
 * Simple dependency graph renderer that emits an ASCII tree.
 *
 * @author Phil Messenger
 */
public class AsciiReportRenderer extends TextProjectReportRenderer implements DependencyReportRenderer {
    private boolean hasConfigs;

    @Override
    public void startProject(Project project) {
        super.startProject(project);
        hasConfigs = false;
    }

    @Override
    public void completeProject(Project project) {
        if (!hasConfigs) {
            getTextOutput().style(Info).println("No configurations").style(Normal);
        }
        super.completeProject(project);
    }

    public void startConfiguration(Configuration configuration) {
        hasConfigs = true;
        getTextOutput().println();
        getTextOutput().style(Identifier).text(configuration.getName()).style(Normal);
        getTextOutput().style(Description).text(getDescription(configuration)).style(Normal);
        getTextOutput().println();
    }

    private String getDescription(Configuration configuration) {
        return GUtil.isTrue(configuration.getDescription()) ? " - " + configuration.getDescription() : "";
    }

    public void completeConfiguration(Configuration configuration) {
    }

    public void render(ResolvedConfiguration resolvedConfiguration) throws IOException {
        Set<MergedResolvedDependency> mergedRoots = mergeChildren(resolvedConfiguration.getFirstLevelModuleDependencies());
        if (mergedRoots.isEmpty()) {
            getTextOutput().style(Info).text("No dependencies").style(Normal).println();
            return;
        }
        renderChildren(mergedRoots, "");
    }

    private void render(MergedResolvedDependency resolvedDependency, String prefix, boolean lastChild) throws IOException {
        getTextOutput().style(Info).text(prefix + "+--- ").style(Normal);
        getTextOutput().text(resolvedDependency.getName());
        getTextOutput().style(Info).format(" [%s]", resolvedDependency.getConfiguration()).style(Normal).println();

        renderChildren(mergeChildren(resolvedDependency.getChildren()), prefix + (lastChild ? "     " : "|    "));
    }

    private void renderChildren(Set<MergedResolvedDependency> children, String prefix) throws IOException {
        List<MergedResolvedDependency> mergedChildren = new ArrayList<MergedResolvedDependency>(children);
        for (int i = 0; i < mergedChildren.size(); i++) {
            MergedResolvedDependency dependency = mergedChildren.get(i);
            render(dependency, prefix, i == mergedChildren.size() - 1);
        }
    }

    private Set<MergedResolvedDependency> mergeChildren(Set<ResolvedDependency> children) {
        Map<String, Set<ResolvedDependency>> mergedGroups = new LinkedHashMap<String, Set<ResolvedDependency>>();
        for (ResolvedDependency child : children) {
            Set<ResolvedDependency> mergeGroup = mergedGroups.get(child.getName());
            if (mergeGroup == null) {
                mergedGroups.put(child.getName(), mergeGroup = new LinkedHashSet<ResolvedDependency>());
            }
            mergeGroup.add(child);
        }
        Set<MergedResolvedDependency> mergedChildren = new LinkedHashSet<MergedResolvedDependency>();
        for (Set<ResolvedDependency> mergedGroup : mergedGroups.values()) {
            mergedChildren.add(new MergedResolvedDependency(mergedGroup));
        }
        return mergedChildren;
    }

    private static class MergedResolvedDependency {
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
    }
}
