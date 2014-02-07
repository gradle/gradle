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
package org.gradle.api.reporting.dependencies.internal

import groovy.json.JsonBuilder
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableModuleResult
import org.gradle.api.tasks.diagnostics.internal.insight.DependencyInsightReporter
import org.gradle.util.GradleVersion

/**
 * Renderer that emits a JSON tree containing the HTML dependency report structure for a given project.
 * The structure is the following:
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
    private final VersionMatcher versionMatcher

    JsonProjectDependencyRenderer(VersionMatcher versionMatcher) {
        this.versionMatcher = versionMatcher
    }

    /**
     * Generates the project dependency report structure
     * @param project the project for which the report must be generated
     * @return the generated JSON, as a String
     */
    String render(Project project) {
        JsonBuilder json = new JsonBuilder();
        renderProject(project, json);
        return json.toString();
    }

    private void renderProject(Project project, json) {
        json gradleVersion : GradleVersion.current().toString(),
             generationDate : new Date().toString(),
             project : [
                 name : project.name,
                 description : project.description,
                 configurations : createConfigurations(project)
             ]
    }

    private List createConfigurations(Project project) {
        return project.configurations.collect { configuration -> [
                name : configuration.name,
                description : configuration.description,
                dependencies : createDependencies(configuration),
                moduleInsights : createModuleInsights(configuration)
            ]
        }
    }

    private List createDependencies(Configuration configuration) {
        ResolutionResult result = configuration.incoming.resolutionResult
        RenderableDependency root = new RenderableModuleResult(result.getRoot())
        Set<ComponentIdentifier> visited = new HashSet<>()
        return createDependencyChildren(root, visited);
    }

    private List createDependencyChildren(RenderableDependency dependency, Set<ComponentIdentifier> visited) {
        dependency.children.collect { childDependency ->
            boolean alreadyVisited = !visited.add(childDependency.id);
            boolean alreadyRendered = alreadyVisited && !childDependency.children.empty
            String name = replaceArrow(childDependency.name)
            boolean hasConflict = name != childDependency.name
            def result = [
                module : getModuleIdentifier(childDependency)?.toString(),
                name : name,
                resolvable : childDependency.resolvable,
                hasConflict : hasConflict,
                alreadyRendered : alreadyRendered,
                children : Collections.emptyList()
            ]
            if (!alreadyRendered) {
                result.children = createDependencyChildren(childDependency, visited)
            }
            return result
        }
    }

    private ModuleIdentifier getModuleIdentifier(RenderableDependency renderableDependency) {
        if(renderableDependency.id instanceof ModuleComponentIdentifier) {
            return new DefaultModuleIdentifier(renderableDependency.id.group, renderableDependency.id.module)
        }
    }

    private List createModuleInsights(Configuration configuration) {
        Set<ModuleIdentifier> modules = collectModules(configuration)
        return modules.collect { module ->
            createModuleInsight(module, configuration)
        }
    }

    private Set<ModuleIdentifier> collectModules(Configuration configuration) {
        ResolutionResult result = configuration.incoming.resolutionResult
        RenderableDependency root = new RenderableModuleResult(result.getRoot())
        Set<ModuleIdentifier> modules = new HashSet<>()
        Set<ComponentIdentifier> visited = new HashSet<>()
        populateModulesWithChildDependencies(root, visited, modules)
        return modules
    }

    private void populateModulesWithChildDependencies(RenderableDependency dependency, Set<ComponentIdentifier> visited, Set<ModuleIdentifier> modules) {
        for (RenderableDependency childDependency : dependency.children) {
            def moduleId = getModuleIdentifier(childDependency)
            modules.add(moduleId)
            boolean alreadyVisited = !visited.add(childDependency.id);
            if (!alreadyVisited) {
                populateModulesWithChildDependencies(childDependency, visited, modules)
            }
        }
    }

    private Map createModuleInsight(ModuleIdentifier module, Configuration configuration) {
        [
            module : module?.toString(),
            insight : createInsight(module, configuration)
        ]
    }

    private List createInsight(ModuleIdentifier module, Configuration configuration) {
        Spec<DependencyResult> dependencySpec = new StrictDependencyResultSpec(module)

        ResolutionResult result = configuration.incoming.resolutionResult;
        Set<DependencyResult> selectedDependencies = new LinkedHashSet<DependencyResult>()

        result.allDependencies { DependencyResult it ->
            if (dependencySpec.isSatisfiedBy(it)) {
                selectedDependencies << it
            }
        }

        Collection<RenderableDependency> sortedDeps = new DependencyInsightReporter().prepare(selectedDependencies, versionMatcher)
        return sortedDeps.collect { dependency ->
            String name = replaceArrow(dependency.name);
            [
                name : replaceArrow(dependency.name),
                description : dependency.description,
                resolvable : dependency.resolvable,
                hasConflict : name != dependency.name,
                children : createInsightDependencyChildren(dependency, new HashSet<ModuleVersionIdentifier>(), configuration)
            ]
        }
    }

    private List createInsightDependencyChildren(RenderableDependency dependency, Set<ModuleVersionIdentifier> visited, Configuration configuration) {
        dependency.children.collect { childDependency ->
            boolean alreadyVisited = !visited.add(childDependency.id);
            boolean leaf = childDependency.children.empty
            boolean alreadyRendered = alreadyVisited && !leaf
            String childName = replaceArrow(childDependency.name);
            boolean hasConflict = childName != childDependency.name;
            String name = leaf ? configuration.name : childName
            def result = [
                    name : name,
                    resolvable : childDependency.resolvable,
                    hasConflict : hasConflict,
                    alreadyRendered : alreadyRendered,
                    isLeaf: leaf,
                    children : Collections.emptyList()
            ]
            if (!alreadyRendered) {
                result.children = createInsightDependencyChildren(childDependency, visited, configuration)
            }
            return result
        }
    }

    private String replaceArrow(String name) {
        return name.replace(' -> ', ' \u27A1 ')
    }
}
