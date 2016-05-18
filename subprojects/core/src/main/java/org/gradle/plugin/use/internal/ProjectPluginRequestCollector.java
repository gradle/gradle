/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.plugin.use.internal;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugin.internal.PluginId;
import org.gradle.plugin.use.PluginDependencySpec;
import org.gradle.plugin.use.ProjectPluginDependenciesSpec;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.gradle.util.CollectionUtils.collect;

/**
 * The delegate of the plugins {} block.
 */
public class ProjectPluginRequestCollector implements ProjectPluginDependenciesSpec, PluginRequestCollector {

    private final ScriptSource scriptSource;
    private final Project project;
    private final List<DependencySpecImpl> specs = new LinkedList<DependencySpecImpl>();
    private final Set<ProjectPluginRequestCollector> children;

    public ProjectPluginRequestCollector(final ScriptSource scriptSource, Project project, final Instantiator instantiator) {
        this.scriptSource = scriptSource;
        this.project = project;
        this.children = collect(project.getSubprojects(), new Transformer<ProjectPluginRequestCollector, Project>() {
            @Override
            public ProjectPluginRequestCollector transform(Project project) {
                return instantiator.newInstance(ProjectPluginRequestCollector.class, scriptSource, project, instantiator);
            }
        });
    }

    @Override
    public PluginDependencySpec id(String id) {
        DependencySpecImpl spec = new DependencySpecImpl(id);
        specs.add(spec);
        return spec;
    }

    @Override
    public void allprojects(Action<? super ProjectPluginDependenciesSpec> configuration) {
        configuration.execute(this);
        subprojects(configuration);
    }

    @Override
    public void subprojects(Action<? super ProjectPluginDependenciesSpec> configuration) {
        for (ProjectPluginRequestCollector child : children) {
            configuration.execute(child);
        }
    }

    public List<TargetedPluginRequest> getRequests() {
        List<TargetedPluginRequest> requests = collect(specs, new Transformer<TargetedPluginRequest, DependencySpecImpl>() {
            public TargetedPluginRequest transform(DependencySpecImpl original) {
                return new TargetedPluginRequest(project, new DefaultPluginRequest(original.id, original.version, scriptSource.getDisplayName()));
            }
        });

        for (ProjectPluginRequestCollector child : children) {
            requests.addAll(child.getRequests());
        }
        return requests;
    }

    private static class DependencySpecImpl implements PluginDependencySpec {
        private final PluginId id;
        private String version;

        private DependencySpecImpl(String id) {
            this.id = PluginId.of(id);
        }

        public void version(String version) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(version), "plugin version cannot be null or empty");
            this.version = version;
        }
    }

}
