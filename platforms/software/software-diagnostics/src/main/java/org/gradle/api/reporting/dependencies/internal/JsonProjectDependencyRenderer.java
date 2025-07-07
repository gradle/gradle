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

import com.google.gson.stream.JsonWriter;
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
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableModuleResult;
import org.gradle.api.tasks.diagnostics.internal.insight.DependencyInsightReporter;
import org.gradle.internal.Actions;
import org.gradle.util.GradleVersion;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Renderer that emits a JSON tree containing the HTML dependency report structure for a given project. The structure is the following:
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
 *                           "resolvable" : "FAILED" | "RESOLVED" | "RESOLVED_CONSTRAINT" | "UNRESOLVED",
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
 *                                   "resolvable" : "FAILED" | "RESOLVED" | "RESOLVED_CONSTRAINT" | "UNRESOLVED",
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
     */
    public void render(ProjectNameAndPath project, Iterable<ConfigurationDetails> configurations, Writer writer) throws IOException {
        JsonWriter json = new JsonWriter(writer);
        writeProject(project, configurations, json);
    }

    private void writeProject(ProjectNameAndPath project, Iterable<ConfigurationDetails> configurations, JsonWriter json) throws IOException {
        json.beginObject();
        json.name("gradleVersion").value(GradleVersion.current().toString());
        json.name("generationDate").value(new Date().toString());
        json.name("project");
        json.beginObject();
        json.name("name").value(project.getName());
        json.name("description").value(project.getDescription());
        json.name("configurations");
        json.beginArray();
        for (ConfigurationDetails configuration : configurations) {
            json.beginObject();
            json.name("name").value(configuration.getName());
            json.name("description").value(configuration.getDescription());
            writeDependencies(configuration, json);
            writeModuleInsights(configuration, json);
            json.endObject();
        }
        json.endArray();
        json.endObject();
        json.endObject();
    }

    private void writeDependencies(ConfigurationDetails configuration, JsonWriter json) throws IOException {
        if (configuration.isCanBeResolved()) {
            RenderableDependency root = new RenderableModuleResult(configuration.getResolutionResultRoot().get());
            writeDependencyChildren(root, new HashSet<>(), json, "dependencies");
        } else {
            writeDependencyChildren(configuration.getUnresolvableResult(), new HashSet<>(), json, "dependencies");
        }
    }

    private void writeDependencyChildren(RenderableDependency dependency, final Set<Object> visited, JsonWriter json, String fieldName) throws IOException {
        Iterable<? extends RenderableDependency> children = dependency.getChildren();
        json.name(fieldName);
        json.beginArray();
        for (RenderableDependency childDependency : children) {
            json.beginObject();
            boolean alreadyVisited = !visited.add(childDependency.getId());
            boolean alreadyRendered = alreadyVisited && !childDependency.getChildren().isEmpty();
            String name = replaceArrow(childDependency.getName());
            boolean hasConflict = !name.equals(childDependency.getName());
            ModuleIdentifier moduleIdentifier = getModuleIdentifier(childDependency);
            json.name("module").value(moduleIdentifier == null ? null : moduleIdentifier.toString());
            json.name("name").value(name);
            json.name("resolvable").value(childDependency.getResolutionState().toString());
            json.name("hasConflict").value(hasConflict);
            json.name("alreadyRendered").value(alreadyRendered);
            if (!alreadyRendered) {
                writeDependencyChildren(childDependency, visited, json, "children");
            } else {
                json.name("children");
                json.beginArray();
                json.endArray();
            }
            json.endObject();

        }
        json.endArray();
    }

    @Nullable
    private ModuleIdentifier getModuleIdentifier(RenderableDependency renderableDependency) {
        if (renderableDependency.getId() instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier id = (ModuleComponentIdentifier) renderableDependency.getId();
            return id.getModuleIdentifier();
        }
        return null;
    }

    private void writeModuleInsights(final ConfigurationDetails configuration, JsonWriter json) throws IOException {
        json.name("moduleInsights");
        json.beginArray();
        for (ModuleIdentifier module : collectModules(configuration)) {
            json.beginObject();
            writeModuleInsight(module, configuration, json);
            json.endObject();
        }
        json.endArray();
    }

    private Set<ModuleIdentifier> collectModules(ConfigurationDetails configuration) {
        RenderableDependency root;
        if (configuration.isCanBeResolved()) {
            root = new RenderableModuleResult(configuration.getResolutionResultRoot().get());
        } else {
            root = configuration.getUnresolvableResult();
        }
        Set<ModuleIdentifier> modules = new HashSet<>();
        Set<ComponentIdentifier> visited = new HashSet<>();
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

    private void writeModuleInsight(ModuleIdentifier module, ConfigurationDetails configuration, JsonWriter json) throws IOException {
        json.name("module").value(module.toString());
        json.name("insight");
        writeInsight(module, configuration.getName(), configuration.getResolutionResultRoot().get(), json);
    }

    private void writeInsight(ModuleIdentifier module, String configurationName, ResolvedComponentResult incomingResolution, JsonWriter json) throws IOException {
        json.beginArray();
        final Spec<DependencyResult> dependencySpec = new StrictDependencyResultSpec(module);

        final Set<DependencyResult> selectedDependencies = new LinkedHashSet<>();
        DefaultResolvedComponentResult.eachElement(incomingResolution, Actions.doNothing(), it -> {
            if (dependencySpec.isSatisfiedBy(it)) {
                selectedDependencies.add(it);
            }
        }, new HashSet<>());

        Collection<RenderableDependency> sortedDeps = new DependencyInsightReporter(versionSelectorScheme, versionComparator, versionParser).convertToRenderableItems(selectedDependencies, false);

        for (RenderableDependency dependency : sortedDeps) {
            json.beginObject();
            String name = replaceArrow(dependency.getName());
            json.name("name").value(replaceArrow(dependency.getName()));
            json.name("description").value(dependency.getDescription());
            json.name("resolvable").value(dependency.getResolutionState().toString());
            json.name("hasConflict").value(!name.equals(dependency.getName()));
            json.name("children");
            writeInsightDependencyChildren(dependency, new HashSet<>(), configurationName, json);
            json.endObject();
        }
        json.endArray();
    }

    private void writeInsightDependencyChildren(RenderableDependency dependency, final Set<Object> visited, final String configurationName, JsonWriter json) throws IOException {
        json.beginArray();
        Iterable<? extends RenderableDependency> children = dependency.getChildren();
        for (RenderableDependency childDependency : children) {
            json.beginObject();
            boolean alreadyVisited = !visited.add(childDependency.getId());
            boolean leaf = childDependency.getChildren().isEmpty();
            boolean alreadyRendered = alreadyVisited && !leaf;
            String childName = replaceArrow(childDependency.getName());
            boolean hasConflict = !childName.equals(childDependency.getName());
            String name = leaf ? configurationName : childName;

            json.name("name").value(name);
            json.name("resolvable").value(childDependency.getResolutionState().toString());
            json.name("hasConflict").value(hasConflict);
            json.name("alreadyRendered").value(alreadyRendered);
            json.name("isLeaf").value(leaf);
            json.name("children");
            if (!alreadyRendered) {
                writeInsightDependencyChildren(childDependency, visited, configurationName, json);
            } else {
                json.beginArray();
                json.endArray();
            }
            json.endObject();
        }
        json.endArray();
    }

    private String replaceArrow(String name) {
        return name.replace(" -> ", " \u27A1 ");
    }

    private final VersionSelectorScheme versionSelectorScheme;
    private final VersionComparator versionComparator;
    private final VersionParser versionParser;
}
