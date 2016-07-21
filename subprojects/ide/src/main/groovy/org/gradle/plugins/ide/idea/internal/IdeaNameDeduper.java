/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.idea.internal;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.gradle.api.Project;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.plugins.ide.internal.configurer.HierarchicalElementDeduplicator;
import org.gradle.plugins.ide.internal.configurer.NameDeduplicationAdapter;

import java.util.Map;
import java.util.Set;

public class IdeaNameDeduper {
    public void configureRoot(Project rootProject) {
        Set<Project> projects = Sets.filter(rootProject.getAllprojects(), HAS_IDEA_PLUGIN);
        Iterable<IdeaModule> ideaModules = Iterables.transform(projects, GET_IDEA_MODULE);
        HierarchicalElementDeduplicator<IdeaModule> deduplicator = new HierarchicalElementDeduplicator<IdeaModule>(new IdeaDeduplicationAdapter());
        Map<IdeaModule, String> deduplicated = deduplicator.deduplicate(ideaModules);
        for (Map.Entry<IdeaModule, String> entry : deduplicated.entrySet()) {
            entry.getKey().setName(entry.getValue());
        }
    }

    private static class IdeaDeduplicationAdapter implements NameDeduplicationAdapter<IdeaModule> {

        @Override
        public String getName(IdeaModule element) {
            return element.getName();
        }

        @Override
        public IdeaModule getParent(IdeaModule element) {
            Project parent = element.getProject().getParent();
            if (parent == null) {
                return null;
            } else {
                return hasIdeaPlugin(parent) ? getModule(parent) : null;
            }
        }
    }

    private static boolean hasIdeaPlugin(Project project) {
        return project.getPlugins().hasPlugin(IdeaPlugin.class);
    }

    private static IdeaModule getModule(Project parent) {
        return parent.getExtensions().getByType(IdeaModel.class).getModule();
    }

    private static final Predicate<Project> HAS_IDEA_PLUGIN = new Predicate<Project>() {
        @Override
        public boolean apply(Project project) {
            return hasIdeaPlugin(project);
        }

    };

    private static final Function<Project, IdeaModule> GET_IDEA_MODULE = new Function<Project, IdeaModule>() {
        @Override
        public IdeaModule apply(Project p) {
            return getModule(p);
        }
    };
}
