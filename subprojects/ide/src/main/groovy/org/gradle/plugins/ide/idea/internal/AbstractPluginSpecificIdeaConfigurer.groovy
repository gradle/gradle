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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.plugins.ide.idea.IdeaPlugin

abstract class AbstractPluginSpecificIdeaConfigurer implements PluginSpecificIdeaConfigurer {

    protected Collection<Project> findAffectedProjects(Project rootProject, Class<? extends Plugin> type) {
        return rootProject.allprojects.findAll {
            it.plugins.hasPlugin(IdeaPlugin) && it.plugins.hasPlugin(type)
        }
    }

    protected Node getOrCreateFacet(Node iml, String type, String name) {
        Node facetManager = getOrCreateFacetManager(iml)
        return getOrCreateNode(facetManager, "facet", [type: type, name: name], ["type"])
    }

    protected Node getOrCreateFacetManager(Node iml) {
        return getOrCreateNode(iml, "component", [name: "FacetManager"])
    }

    protected Node getOrCreateFacetConfiguration(Node facet) {
        return getOrCreateNode(facet, "configuration")
    }

    protected Node getOrCreateArtifact(Node ipr, String type, String name) {
        Node artifactManager = getOrCreateArtifactManager(ipr)
        return getOrCreateNode(artifactManager, "artifact", [type: type, name:  name])
    }

    protected Node getOrCreateArtifactManager(Node ipr) {
        return getOrCreateNode(ipr, "component", [name: "ArtifactManager"])
    }

    protected Node getOrCreateNode(Node parent, String name) {
        return getOrCreateNode(parent, name, [:], [])
    }

    protected Node getOrCreateNode(Node parent, String name, Map<String, String> attributes) {
        return getOrCreateNode(parent, name, attributes, attributes.keySet())
    }

    protected Node getOrCreateNode(Node parent, String name, Map<String, String> attributes,
                                   Collection<String> searchRelevantAttributes) {
        Node node = findNode(parent, name, attributes, searchRelevantAttributes)
        if (!node) {
            node = parent.appendNode(name, attributes)
        }
        return node
    }

    protected Node createOrReplaceNode(Node parent, String name, Map<String, String> attributes) {
        return createOrReplaceNode(parent, name, attributes, attributes.keySet())
    }

    protected Node createOrReplaceNode(Node parent, String name, Map<String, String> attributes,
                                       Collection<String> searchRelevantAttributes) {
        Node node = findNode(parent, name, attributes, searchRelevantAttributes)
        if (node) {
            parent.remove(node)
        }
        return parent.appendNode(name, attributes)
    }

    protected Node findNode(Node parent, String name, Map<String, String> attributes,
                            Collection<String> searchRelevantAttributes) {
        return parent.find { it.name() == name && searchRelevantAttributes.findAll {
                key -> it.attribute(key) == attributes.get(key)
            }.size() == searchRelevantAttributes.size()
        }
    }
}
