/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.internal;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Project;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.GenerateEclipseProject;
import org.gradle.plugins.ide.eclipse.model.EclipseProject;
import org.gradle.plugins.ide.internal.configurer.HierarchicalElementDeduplicator;
import org.gradle.plugins.ide.internal.configurer.NameDeduplicationAdapter;

import java.util.Map;
import java.util.Set;

public class EclipseNameDeduper {

    public void configureRoot(Project rootProject) {
        Set<Project> projects = Sets.filter(rootProject.getAllprojects(), HAS_ECLIPSE_PLUGIN);
        ImmutableMap<EclipseProject, Project> eclipseProjects = Maps.uniqueIndex(projects, GET_ECLIPSE_PROJECT);
        HierarchicalElementDeduplicator<EclipseProject> deduplicator = new HierarchicalElementDeduplicator<EclipseProject>(new EclipseDeduplicationAdapter(eclipseProjects));
        Map<EclipseProject, String> deduplicated = deduplicator.deduplicate(eclipseProjects.keySet());
        for (Map.Entry<EclipseProject, String> entry : deduplicated.entrySet()) {
            entry.getKey().setName(entry.getValue());
        }
    }

    private static class EclipseDeduplicationAdapter implements NameDeduplicationAdapter<EclipseProject> {
        final Map<EclipseProject, Project> eclipseProjects;

        private EclipseDeduplicationAdapter(Map<EclipseProject, Project> eclipseProjects) {
            this.eclipseProjects = eclipseProjects;
        }

        @Override
        public String getName(EclipseProject element) {
            return element.getName();
        }

        @Override
        public EclipseProject getParent(EclipseProject element) {
            Project parent = eclipseProjects.get(element).getParent();
            if (parent == null) {
                return null;
            } else {
                return hasEclipsePlugin(parent) ? getEclipseProject(parent) : null;
            }
        }
    }

    private static EclipseProject getEclipseProject(Project project) {
        return ((GenerateEclipseProject) project.getTasks().getByName("eclipseProject")).getProjectModel();
    }

    private static boolean hasEclipsePlugin(Project project) {
        return project.getPlugins().hasPlugin(EclipsePlugin.class);
    }

    private static final Predicate<Project> HAS_ECLIPSE_PLUGIN = new Predicate<Project>() {
        @Override
        public boolean apply(Project project) {
            return hasEclipsePlugin(project);
        }
    };

    private static final Function<Project, EclipseProject> GET_ECLIPSE_PROJECT = new Function<Project, EclipseProject>() {
        @Override
        public EclipseProject apply(Project p) {
            return getEclipseProject(p);
        }
    };
}
