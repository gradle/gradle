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

package org.gradle.api.reporting.dependencies.internal;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import groovy.json.JsonBuilder;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.result.DefaultResolvedComponentResult;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.diagnostics.internal.ConfigurationDetails;
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails.ProjectNameAndPath;
import org.gradle.api.tasks.diagnostics.internal.ProjectsWithConfigurations;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableModuleResult;
import org.gradle.api.tasks.diagnostics.internal.insight.DependencyInsightReporter;
import org.gradle.internal.Actions;
import org.gradle.util.GradleVersion;
import org.gradle.util.internal.CollectionUtils;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renderer that emits a JSON tree containing the dependency report structure for a given project. The structure is the following:
 *
 * <pre>
 *     {
 *          "gradleVersion" : "...",
 *          "generationDate" : "...",
 *          "project" : {
 *               "name" : "...",
 *               "description : "...", (optional)
 *               "configurations" : [
 *                   "name" : "...",
 *                   "description" : "...", (optional)
 *                   "dependencies" : [
 *                       {
 *                           "module" : "group:name"
 *                           "name" : "...",
 *                           "resolvable" : true|false,
 *                           "alreadyRendered" : true|false
 *                           "hasConflict" : true|false
 *                           "children" : [
 *                               same array as configurations.dependencies.children
 *                           ]
 *                       },
 *                       ...
 *                   ],
 *                   "moduleInsights : [
 *                       {
 *                           "module" : "group:name"
 *                           "insight" : [
 *                               {
 *                                   "name" : "...",
 *                                   "description" : "...",
 *                                   "resolvable" : true|false,
 *                                   "hasConflict" : true|false,
 *                                   "children": [
 *                                       {
 *                                           "name" : "...",
 *                                           "resolvable" : "...",
 *                                           "hasConflict" : true|false,
 *                                           "alreadyRendered" : true|false
 *                                           "isLeaf" : true|false
 *                                           "children" : [
 *                                               same array as configurations.moduleInsights.insight.children
 *                                           ]
 *                                       },
 *                                       ...
 *                                   ]
 *                               },
 *                               ...
 *                           ]
 *                       }
 *                       ,
 *                       ...
 *                   ]
 *               ]
 *          }
 *      }
 * </pre>
 */
class JsonProjectDependencyRenderer {
    public JsonProjectDependencyRenderer(VersionSelectorScheme versionSelectorScheme, VersionComparator versionComparator, VersionParser versionParser) {
        this.versionSelectorScheme = versionSelectorScheme;
        this.versionComparator = versionComparator;
        this.versionParser = versionParser;
    }

    /**
     * Generates the project dependency report structure
     *
     * @param project the project for which the report must be generated
     * @return the generated JSON, as a String
     */
    public String render(ProjectNameAndPath project, Iterable<ConfigurationDetails> configurations) {
        JsonBuilder json = new JsonBuilder();
        renderProject(project, configurations, json);
        return json.toString();
    }

    /**
     * Generates the project dependency report structures for each project
     *
     * @param projectsWithConfigurations the projects with configurations for which the reports must be generated
     * @return the generated JSON, as a String
     */
    public String render(ProjectsWithConfigurations<ProjectNameAndPath, ConfigurationDetails> projectsWithConfigurations) {
        JsonBuilder json = new JsonBuilder();
        renderProjects(projectsWithConfigurations, json);
        return json.toString();
    }

    // Historic note: this class still uses the Groovy JsonBuilder, as it was originally developed as a Groovy class.
    private void renderProject(ProjectNameAndPath project, Iterable<ConfigurationDetails> configurations, JsonBuilder json) {

        Map<String, Object> overall = Maps.newLinkedHashMap();
        overall.put("gradleVersion", GradleVersion.current().toString());
        overall.put("generationDate", new Date().toString());

        Map<String, Object> projectOut = Maps.newLinkedHashMap();
        projectOut.put("name", project.getName());
        projectOut.put("description", project.getDescription());
        projectOut.put("configurations", createConfigurations(configurations));
        overall.put("project", projectOut);

        json.call(overall);
    }

    private void renderProjects(ProjectsWithConfigurations<ProjectNameAndPath, ConfigurationDetails> projectsWithConfigurations, JsonBuilder json) {

        Map<String, Object> overall = Maps.newLinkedHashMap();
        overall.put("gradleVersion", GradleVersion.current().toString());
        overall.put("generationDate", new Date().toString());

        List<Map<String, Object>> projectsOut = projectsWithConfigurations.getProjects().stream()
            .map(p -> {
                Map<String, Object> projectOut = Maps.newLinkedHashMap();
                projectOut.put("name", p.getName());
                projectOut.put("description", p.getDescription());
                projectOut.put("configurations", createConfigurations(projectsWithConfigurations.getConfigurationsFor(p)));
                return projectOut;
            })
            .collect(Collectors.toList());
        overall.put("projects", projectsOut);

        json.call(overall);
    }

    private List<Map<String, Object>> createConfigurations(Iterable<ConfigurationDetails> configurations) {
        return CollectionUtils.collect(configurations, configuration -> {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>(4);
            map.put("name", configuration.getName());
            map.put("description", configuration.getDescription());
            map.put("dependencies", createDependencies(configuration));
            map.put("moduleInsights", createModuleInsights(configuration));
            return map;
        });
    }

    private List<Map<String, Object>> createDependencies(ConfigurationDetails configuration) {
        if (configuration.isCanBeResolved()) {
            RenderableDependency root = new RenderableModuleResult(configuration.getResolutionResultRoot().get());
            return createDependencyChildren(root, new HashSet<>());
        } else {
            return createDependencyChildren(configuration.getUnresolvableResult(), new HashSet<>());
        }
    }

    private List<Map<String, Object>> createDependencyChildren(RenderableDependency dependency, final Set<Object> visited) {
        Iterable<? extends RenderableDependency> children = dependency.getChildren();
        return CollectionUtils.collect(children, childDependency -> {
            boolean alreadyVisited = !visited.add(childDependency.getId());
            boolean alreadyRendered = alreadyVisited && !childDependency.getChildren().isEmpty();
            String name = replaceArrow(childDependency.getName());
            boolean hasConflict = !name.equals(childDependency.getName());
            LinkedHashMap<String, Object> map = new LinkedHashMap<>(6);
            ModuleIdentifier moduleIdentifier = getModuleIdentifier(childDependency);
            map.put("module", moduleIdentifier == null ? null : moduleIdentifier.toString());
            map.put("name", name);
            map.put("resolvable", childDependency.getResolutionState());
            map.put("hasConflict", hasConflict);
            map.put("alreadyRendered", alreadyRendered);
            map.put("children", Collections.emptyList());
            if (!alreadyRendered) {
                map.put("children", createDependencyChildren(childDependency, visited));
            }
            return map;
        });
    }

    @Nullable
    private ModuleIdentifier getModuleIdentifier(RenderableDependency renderableDependency) {
        if (renderableDependency.getId() instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier id = (ModuleComponentIdentifier) renderableDependency.getId();
            return id.getModuleIdentifier();
        }
        return null;
    }

    private List<Object> createModuleInsights(final ConfigurationDetails configuration) {
        Iterable<ModuleIdentifier> modules = collectModules(configuration);
        return CollectionUtils.collect(modules, moduleIdentifier -> createModuleInsight(moduleIdentifier, configuration));
    }

    private Set<ModuleIdentifier> collectModules(ConfigurationDetails configuration) {
        RenderableDependency root;
        if (configuration.isCanBeResolved()) {
            root = new RenderableModuleResult(configuration.getResolutionResultRoot().get());
        } else {
            root = configuration.getUnresolvableResult();
        }
        Set<ModuleIdentifier> modules = Sets.newHashSet();
        Set<ComponentIdentifier> visited = Sets.newHashSet();
        populateModulesWithChildDependencies(root, visited, modules);
        return modules;
    }

    private void populateModulesWithChildDependencies(RenderableDependency dependency, Set<ComponentIdentifier> visited, Set<ModuleIdentifier> modules) {
        for (RenderableDependency childDependency : dependency.getChildren()) {
            ModuleIdentifier moduleId = getModuleIdentifier(childDependency);
            if (moduleId == null) {
                continue;
            }
            modules.add(moduleId);
            boolean alreadyVisited = !visited.add((ComponentIdentifier) childDependency.getId());
            if (!alreadyVisited) {
                populateModulesWithChildDependencies(childDependency, visited, modules);
            }
        }
    }

    private Map<String, Object> createModuleInsight(ModuleIdentifier module, ConfigurationDetails configuration) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>(2);
        map.put("module", module.toString());
        map.put("insight", createInsight(module, configuration.getName(), configuration.getResolutionResultRoot().get()));
        return map;
    }

    private List<Object> createInsight(ModuleIdentifier module, String configurationName, ResolvedComponentResult incomingResolution) {
        final Spec<DependencyResult> dependencySpec = new StrictDependencyResultSpec(module);

        final Set<DependencyResult> selectedDependencies = new LinkedHashSet<>();
        DefaultResolvedComponentResult.eachElement(incomingResolution, Actions.doNothing(), it -> {
            if (dependencySpec.isSatisfiedBy(it)) {
                selectedDependencies.add(it);
            }
        }, new HashSet<>());

        Collection<RenderableDependency> sortedDeps = new DependencyInsightReporter(versionSelectorScheme, versionComparator, versionParser).convertToRenderableItems(selectedDependencies, false);
        return CollectionUtils.collect(sortedDeps, dependency -> {
            String name = replaceArrow(dependency.getName());
            LinkedHashMap<String, Object> map = new LinkedHashMap<>(5);
            map.put("name", replaceArrow(dependency.getName()));
            map.put("description", dependency.getDescription());
            map.put("resolvable", dependency.getResolutionState());
            map.put("hasConflict", !name.equals(dependency.getName()));
            map.put("children", createInsightDependencyChildren(dependency, new HashSet<>(), configurationName));
            return map;
        });
    }

    private List<Object> createInsightDependencyChildren(RenderableDependency dependency, final Set<Object> visited, final String configurationName) {
        Iterable<? extends RenderableDependency> children = dependency.getChildren();
        return CollectionUtils.collect(children, childDependency -> {
            boolean alreadyVisited = !visited.add(childDependency.getId());
            boolean leaf = childDependency.getChildren().isEmpty();
            boolean alreadyRendered = alreadyVisited && !leaf;
            String childName = replaceArrow(childDependency.getName());
            boolean hasConflict = !childName.equals(childDependency.getName());
            String name = leaf ? configurationName : childName;

            LinkedHashMap<String, Object> map = new LinkedHashMap<>(6);
            map.put("name", name);
            map.put("resolvable", childDependency.getResolutionState());
            map.put("hasConflict", hasConflict);
            map.put("alreadyRendered", alreadyRendered);
            map.put("isLeaf", leaf);
            map.put("children", Collections.emptyList());
            if (!alreadyRendered) {
                map.put("children", createInsightDependencyChildren(childDependency, visited, configurationName));
            }
            return map;
        });
    }

    private String replaceArrow(String name) {
        return name.replace(" -> ", " \u27A1 ");
    }

    private final VersionSelectorScheme versionSelectorScheme;
    private final VersionComparator versionComparator;
    private final VersionParser versionParser;
}
