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

package org.gradle.plugins.ide.idea.internal

import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.plugins.ide.idea.model.FilePath

class WarSpecificIdeaConfigurer extends AbstractPluginSpecificIdeaConfigurer {
    void configure(Project rootProject) {
        rootProject.gradle.projectsEvaluated {
            def webProjects = findAffectedProjects(rootProject, WarPlugin)
            rootProject.configure(webProjects) { Project project ->
                idea.module.iml.withXml { XmlProvider xmlProvider ->
                    declareWebFacet(project, xmlProvider.asNode())
                }
                rootProject.idea.project.ipr.withXml { XmlProvider xmlProvider ->
                    declareWebExplodedArtifact(project, xmlProvider.asNode())
                }
            }
        }
    }

    private void declareWebFacet(Project project, Node iml) {
        def webFacet = getOrCreateFacet(iml, "web", "Web")
        def configuration = getOrCreateFacetConfiguration(webFacet)

        def descriptors = getOrCreateNode(configuration, "descriptors")
        def deploymentDescriptor = getOrCreateNode(descriptors, "deploymentDescriptor")
        deploymentDescriptor.@name = "web.xml"
        deploymentDescriptor.@url = getIdeaModulePath(project, new File(project.webAppDir, "WEB-INF/web.xml")).url

        def webRoots = getOrCreateNode(configuration, "webroots")
        def root = getOrCreateNode(webRoots, "root")
        root.@url = getIdeaModulePath(project, project.webAppDir).url
        root.@relative = "/"

        def sourceRoots = getOrCreateNode(configuration, "sourceRoots")
        project.sourceSets.main.allSource.srcDirs.flatten().each {
            def url = getIdeaModulePath(project, it).url
            if (!sourceRoots.root.find { it.@url == url } ) {
                sourceRoots.appendNode("root", ["url": url])
            }
        }
    }

    private void declareWebExplodedArtifact(Project project, Node ipr) {
        Node artifact = getOrCreateArtifact(ipr, "exploded-war", "${project.name}:Web exploded")
        if (artifact.get("output-path")[0] == null) {
            def outputPath = new File(getProjectBuildDir(project), "${project.name}_web_exploded")
            artifact.appendNode("output-path", getIdeaProjectPath(project, outputPath).url)
        }

        Node rootNode = getOrCreateNode(artifact, "root")
        getOrCreateNode(rootNode, "element", [id: "javaee-facet-resources", facet: "${project.name}/web/Web"])

        Node webInfDirectory = getOrCreateNode(rootNode, "element", [id: "directory", name: "WEB-INF"])

        Node classesDirectory = createOrReplaceNode(webInfDirectory, "element", [id: "directory", name: "classes"])
        getOrCreateNode(classesDirectory, "element", [id: "module-output", name: project.name])
        Node libDirectory = createOrReplaceNode(webInfDirectory, "element", [id: "directory", name: "lib"])
        addDependencies(project, project.configurations."$JavaPlugin.RUNTIME_CONFIGURATION_NAME", classesDirectory, libDirectory)
    }

    private void addDependencies(Project project, Configuration configuration, Node classesDirectory, Node libDirectory) {
        if (configuration.name != WarPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME
                && configuration.name != WarPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME) {
            configuration.dependencies.each {
                if (it instanceof ProjectDependency) {
                    getOrCreateNode(classesDirectory, "element", [id: "module-output", name: it.dependencyProject.name])
                } else {
                    project.configurations.runtime.files(it).each { file ->
                        getOrCreateNode(libDirectory, "element", [id: "file-copy", path: getIdeaProjectPath(project, file).relPath])
                    }
                }
            }
            configuration.getExtendsFrom().each { addDependencies(project, it, classesDirectory, libDirectory) }
        }
    }

    private File getProjectBuildDir(Project project) {
        return (project.idea.module.outputDir == null) ?
            project.rootProject.file("out") : project.idea.module.outputDir
    }

    private FilePath getIdeaModulePath(Project project, File file) {
        return project.idea.module.pathFactory.path(file)
    }

    private FilePath getIdeaProjectPath(Project project, File file) {
        return project.rootProject.idea.project.pathFactory.path(file)
    }
}
