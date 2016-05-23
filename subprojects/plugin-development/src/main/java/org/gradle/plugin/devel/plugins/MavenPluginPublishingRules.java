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

package org.gradle.plugin.devel.plugins;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.XmlProvider;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.component.SoftwareComponentContainer;
import org.gradle.api.publish.PublicationContainer;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.model.Finalize;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension;
import org.gradle.plugin.devel.PluginDeclaration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static org.gradle.plugin.use.resolve.internal.ArtifactRepositoryPluginResolver.PLUGIN_MARKER_SUFFIX;

class MavenPluginPublishingRules extends RuleSource {

    @Mutate
    public void addMainPublication(PublishingExtension publishing, GradlePluginDevelopmentExtension pluginDevelopment, ServiceRegistry services) {
        if (!pluginDevelopment.isAutomatedPublishing()) {
            return;
        }
        SoftwareComponentContainer componentContainer = services.get(SoftwareComponentContainer.class);
        SoftwareComponent component = componentContainer.getByName("java");

        PublicationContainer publications = publishing.getPublications();
        createMavenPluginPublication(component, publications);
    }
    @Finalize
    public void addMarkerPublications(PublishingExtension publishing, GradlePluginDevelopmentExtension pluginDevelopment) {
        if (!pluginDevelopment.isAutomatedPublishing()) {
            return;
        }

        PublicationContainer publications = publishing.getPublications();
        NamedDomainObjectContainer<PluginDeclaration> declaredPlugins = pluginDevelopment.getPlugins();

        for (PluginDeclaration declaration : declaredPlugins) {
            createMavenMarkerPublication(declaration, (MavenPublication) publications.getByName("pluginMaven"), publications);
        }
    }
    private void createMavenPluginPublication(SoftwareComponent component, PublicationContainer publications) {
        MavenPublication publication = publications.create("pluginMaven", MavenPublication.class);
        publication.from(component);
    }

    private void createMavenMarkerPublication(PluginDeclaration declaration, final MavenPublication coordinates, PublicationContainer publications) {
        String pluginId = declaration.getId();
        MavenPublication publication = publications.create(declaration.getName() + "PluginMarkerMaven", MavenPublication.class);
        publication.setArtifactId(pluginId + PLUGIN_MARKER_SUFFIX);
        publication.setGroupId(pluginId);
        publication.getPom().withXml(new Action<XmlProvider>() {
            @Override
            public void execute(XmlProvider xmlProvider) {
                Element root = xmlProvider.asElement();
                Document document = root.getOwnerDocument();
                Node dependencies = root.appendChild(document.createElement("dependencies"));
                Node dependency = dependencies.appendChild(document.createElement("dependency"));
                Node groupId = dependency.appendChild(document.createElement("groupId"));
                groupId.setTextContent(coordinates.getGroupId());
                Node artifactId = dependency.appendChild(document.createElement("artifactId"));
                artifactId.setTextContent(coordinates.getArtifactId());
                Node version = dependency.appendChild(document.createElement("version"));
                version.setTextContent(coordinates.getVersion());
            }
        });
    }
}
