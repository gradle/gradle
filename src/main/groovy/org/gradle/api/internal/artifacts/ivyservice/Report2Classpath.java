/*
 * Copyright 2007-2009 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.core.resolve.IvyNodeCallers;
import org.gradle.api.artifacts.Dependency;
import org.gradle.util.Clock;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class Report2Classpath {
    private static Logger logger = LoggerFactory.getLogger(Report2Classpath.class);

    public Set<File> getClasspath(ResolveReport resolveReport, Set<Dependency> dependencies) {
        Clock clock = new Clock();
        Set<ModuleRevisionId> firstLevelDependenciesModuleRevisionIds = createFirstLevelDependenciesModuleRevisionIds(dependencies);
        Set<File> files = new HashSet<File>();
        Set<ModuleRevisionId> transitiveFirstLevelDependenciesModuleRevisionIds = new HashSet<ModuleRevisionId>();
        for (Iterator iterator = resolveReport.getDependencies().iterator(); iterator.hasNext();) {
            IvyNode ivyNode = (IvyNode) iterator.next();
            if (ivyNode.isLoaded()) {
                ModuleRevisionId normalizedIvyNodeId = normalize(ivyNode.getId());
                boolean hasAssociatedFirstLevelDependencyNodes = false;
                if (isNodeFirstLevelDependency(normalizedIvyNodeId, firstLevelDependenciesModuleRevisionIds)) {
                    hasAssociatedFirstLevelDependencyNodes = true;
                } else {
                    hasAssociatedFirstLevelDependencyNodes = hasParentFirstLevelDependency(transitiveFirstLevelDependenciesModuleRevisionIds,
                            ivyNode);
                }
                if (!hasAssociatedFirstLevelDependencyNodes) {
                    continue;
                }
                transitiveFirstLevelDependenciesModuleRevisionIds.add(ivyNode.getResolvedId());
                files.addAll(getFilesForReport(resolveReport.getArtifactsReports(ivyNode.getId())));
            }
        }
        logger.debug("Timing: Getting dependency files took {}", clock.getTime());
        return files;
    }

    private boolean hasParentFirstLevelDependency(Set<ModuleRevisionId> transitiveFirstLevelDependenciesModuleRevisionIds,
                                                        IvyNode ivyNode) {
        for (IvyNodeCallers.Caller caller : ivyNode.getAllRealCallers()) {
            if (transitiveFirstLevelDependenciesModuleRevisionIds.contains(caller.getModuleRevisionId())) {
                return true;
            }
        }
        return false;
    }

    private boolean isNodeFirstLevelDependency(ModuleRevisionId normalizedIvyNodeId, Set<ModuleRevisionId> firstLevelDependencies2Nodes) {
        return firstLevelDependencies2Nodes.contains(normalizedIvyNodeId);
    }

    private Set<ModuleRevisionId> createFirstLevelDependenciesModuleRevisionIds(Set<Dependency> firstLevelDependencies) {
        Set<ModuleRevisionId> firstLevelDependenciesModuleRevisionIds = new LinkedHashSet<ModuleRevisionId>();
        for (Dependency firstLevelDependency : firstLevelDependencies) {
            ModuleRevisionId moduleRevisionId = ModuleRevisionId.newInstance(
                    GUtil.elvis(firstLevelDependency.getGroup(), ""),
                    firstLevelDependency.getName(),
                    GUtil.elvis(firstLevelDependency.getVersion(), ""));
            firstLevelDependenciesModuleRevisionIds.add(moduleRevisionId);
        }
        return firstLevelDependenciesModuleRevisionIds;
    }

    /*
     * Gradle has a different notion of equality then Ivy. We need to map the download reports to
     * moduleRevisionIds that are only use fields relevant for Gradle equality.
     */
    private ModuleRevisionId normalize(ModuleRevisionId moduleRevisionId) {
        return ModuleRevisionId.newInstance(
                moduleRevisionId.getOrganisation(),
                moduleRevisionId.getName(),
                moduleRevisionId.getRevision());
    }

    private Set<File> getFilesForReport(ArtifactDownloadReport[] artifactDownloadReports) {
        Set<File> files = new LinkedHashSet<File>();
        if (artifactDownloadReports != null) {
            for (ArtifactDownloadReport artifactDownloadReport : artifactDownloadReports) {
                files.add(artifactDownloadReport.getLocalFile());
            }
        }
        return files;
    }
}
