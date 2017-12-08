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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.CompositeVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentStateWithDependents;
import org.gradle.internal.component.model.ComponentResolveMetadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class LatestModuleConflictResolver implements ModuleConflictResolver {
    private final Comparator<Version> versionComparator;

    LatestModuleConflictResolver(VersionComparator versionComparator) {
        this.versionComparator = versionComparator.asVersionComparator();
    }

    @Override
    public <T extends ComponentResolutionState> void select(ConflictResolverDetails<T> details) {
        // Find the candidates with the highest base version
        Version baseVersion = null;
        Map<Version, T> matches = new LinkedHashMap<Version, T>();
        Collection<? extends T> candidates = details.getCandidates();
        for (T candidate : candidates) {
            Version version = VersionParser.INSTANCE.transform(candidate.getVersion());
            if (baseVersion == null || versionComparator.compare(version.getBaseVersion(), baseVersion) > 0) {
                boolean accept = true;
                for (T t : candidates) {
                    ResolvedVersionConstraint candidateConstraints = t.getVersionConstraint();
                    if (t != candidate && candidateConstraints != null) { // may be null for local components
                        VersionSelector rejectedVersionSelector = candidateConstraints.getRejectedSelector();
                        if (rejectedVersionSelector != null && rejectedVersionSelector.accept(version)) {
                            accept = false;
                            break;
                        }
                    }
                }
                baseVersion = version.getBaseVersion();
                if (accept) {
                    matches.put(version, candidate);
                } else {
                    matches.clear();
                }
            } else if (version.getBaseVersion().equals(baseVersion)) {
                matches.put(version, candidate);
            }
        }
        if (matches.isEmpty()) {
            boolean hasRejectAll = false;
            for (T candidate : candidates) {
                hasRejectAll |= isRejectAll(candidate.getVersionConstraint());
            }
            StringBuilder sb = new StringBuilder();
            if (hasRejectAll) {
                sb.append("Module ");
            } else {
                sb.append("Cannot find a version of ");
            }
            boolean first = true;
            for (T candidate : candidates) {
                if (first) {
                    sb.append("'").append(candidate.getId().getModule()).append("'");
                    if (hasRejectAll) {
                        sb.append(" has been rejected:\n");
                    } else {
                        sb.append(" that satisfies the version constraints: \n");
                    }
                }
                if (candidate instanceof ComponentStateWithDependents) {
                    ComponentStateWithDependents component = (ComponentStateWithDependents) candidate;
                    List<String> paths = pathTo(component);
                    for (String path : paths) {
                        sb.append("   ").append(path).append("\n");
                    }
                } else {
                    sb.append(renderVersionConstraint(candidate.getVersionConstraint()));
                }
                first = false;
            }
            details.fail(new GradleException(sb.toString()));
            return;
        }

        if (matches.size() == 1) {
            details.select(matches.values().iterator().next());
            return;
        }

        // Work backwards from highest version, return the first candidate with qualified version and release status, or candidate with unqualified version
        List<Version> sorted = new ArrayList<Version>(matches.keySet());
        Collections.sort(sorted, Collections.reverseOrder(versionComparator));
        for (Version version : sorted) {
            T component = matches.get(version);
            if (!version.isQualified()) {
                details.select(component);
                return;
            }
            ComponentResolveMetadata metaData = component.getMetaData();
            if (metaData != null && "release".equals(metaData.getStatus())) {
                details.select(component);
                return;
            }
        }

        // Nothing - just return the highest version
        details.select(matches.get(sorted.get(0)));
    }

    private List<String> pathTo(ComponentStateWithDependents component) {
        List<List<ComponentStateWithDependents>> acc = Lists.newArrayListWithExpectedSize(1);
        pathTo(component, Lists.<ComponentStateWithDependents>newArrayList(), acc);
        List<String> result = Lists.newArrayListWithCapacity(acc.size());
        for (List<ComponentStateWithDependents> path : acc) {
            ComponentStateWithDependents target = Iterators.getLast(path.iterator());
            StringBuilder sb = new StringBuilder();
            if (target.isFromPendingNode()) {
                sb.append("Constraint path ");
            } else {
                sb.append("Dependency path ");
            }
            for (Iterator<ComponentStateWithDependents> iterator = path.iterator(); iterator.hasNext();) {
                ComponentStateWithDependents e = iterator.next();
                ModuleVersionIdentifier id = e.getId();
                if (iterator.hasNext()) {
                    sb.append('\'').append(id).append('\'');
                    sb.append(" --> ");
                } else {
                    sb.append('\'').append(id.getGroup()).append(':').append(id.getName()).append('\'');
                    sb.append(" ").append(renderVersionConstraint(e.getVersionConstraint()));
                }
            }
            result.add(sb.toString());
        }
        return result;
    }

    private void pathTo(ComponentStateWithDependents component, List<ComponentStateWithDependents> currentPath, List<List<ComponentStateWithDependents>> accumulator) {
        currentPath.add(0, component);
        Collection<ComponentState> dependents = component.getDependents();
        List<ComponentState> unattachedDependencies = component.getUnattachedDependencies();
        Set<ComponentState> allDependents = Sets.newLinkedHashSet();
        allDependents.addAll(dependents);
        allDependents.addAll(unattachedDependencies);
        for (ComponentStateWithDependents dependent : allDependents) {
            List<ComponentStateWithDependents> otherPath = Lists.newArrayList(currentPath);
            pathTo(dependent, otherPath, accumulator);
        }
        if (allDependents.isEmpty()) {
            accumulator.add(currentPath);
        }
    }

    private static String renderVersionConstraint(ResolvedVersionConstraint constraint) {
        if (isRejectAll(constraint)) {
            return "rejects all versions";
        }
        VersionSelector preferredSelector = constraint.getPreferredSelector();
        VersionSelector rejectedSelector = constraint.getRejectedSelector();
        StringBuilder sb = new StringBuilder("prefers ");
        sb.append('\'');
        sb.append(preferredSelector.getSelector());
        sb.append('\'');
        if (rejectedSelector != null) {
            sb.append(", rejects ");
            if (rejectedSelector instanceof CompositeVersionSelector) {
                sb.append("any of \"");
                int i = 0;
                for (VersionSelector selector : ((CompositeVersionSelector) rejectedSelector).getSelectors()) {
                    if (i++ > 0) {
                        sb.append(", ");
                    }
                    sb.append('\'');
                    sb.append(selector.getSelector());
                    sb.append('\'');
                }
                sb.append("\"");
            } else {
                sb.append('\'');
                sb.append(rejectedSelector.getSelector());
                sb.append('\'');
            }
        }
        return sb.toString();
    }

    private static boolean isRejectAll(ResolvedVersionConstraint constraint) {
        return "".equals(constraint.getPreferredVersion())
            && hasMatchAllSelector(constraint.getRejectedVersions());
    }

    private static boolean hasMatchAllSelector(List<String> rejectedVersions) {
        for (String version : rejectedVersions) {
            if ("+".equals(version)) {
                return true;
            }
        }
        return false;
    }
}
