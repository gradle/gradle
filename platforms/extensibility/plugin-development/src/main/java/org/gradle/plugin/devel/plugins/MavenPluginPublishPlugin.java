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
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.XmlProvider;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.PublicationContainer;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal;
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension;
import org.gradle.plugin.devel.PluginDeclaration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.inject.Inject;

import static org.gradle.plugin.use.resolve.internal.ArtifactRepositoriesPluginResolver.PLUGIN_MARKER_SUFFIX;

abstract class MavenPluginPublishPlugin implements Plugin<Project> {

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @Inject
    public MavenPluginPublishPlugin() {
        // This class is not visible outside of this package.
        // To instantiate this plugin, we need a protected constructor.
    }

    @Override
    public void apply(Project project) {
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(final Project project) {
                configurePublishing(project);
            }
        });
    }

    private void configurePublishing(final Project project) {
        project.getExtensions().configure(PublishingExtension.class, new Action<PublishingExtension>() {
            @Override
            public void execute(PublishingExtension publishing) {
                final GradlePluginDevelopmentExtension pluginDevelopment = project.getExtensions().getByType(GradlePluginDevelopmentExtension.class);
                if (!pluginDevelopment.isAutomatedPublishing()) {
                    return;
                }
                SoftwareComponent mainComponent = project.getComponents().getByName("java");
                MavenPublication mainPublication = addMainPublication(publishing, mainComponent);
                addMarkerPublications(mainPublication, publishing, pluginDevelopment);
            }
        });
    }

    private MavenPublication addMainPublication(PublishingExtension publishing, SoftwareComponent mainComponent) {
        MavenPublication publication = publishing.getPublications().maybeCreate("pluginMaven", MavenPublication.class);
        publication.from(mainComponent);
        return publication;
    }

    private void addMarkerPublications(MavenPublication mainPublication, PublishingExtension publishing, GradlePluginDevelopmentExtension pluginDevelopment) {
        for (PluginDeclaration declaration : pluginDevelopment.getPlugins()) {
            createMavenMarkerPublication(declaration, mainPublication, publishing.getPublications());
        }
    }

    private void createMavenMarkerPublication(PluginDeclaration declaration, final MavenPublication coordinates, PublicationContainer publications) {
        String pluginId = declaration.getId();
        MavenPublicationInternal publication = (MavenPublicationInternal) publications.create(declaration.getName() + "PluginMarkerMaven", MavenPublication.class);
        publication.setAlias(true);
        publication.setArtifactId(pluginId + PLUGIN_MARKER_SUFFIX);
        publication.setGroupId(pluginId);

        Provider<String> groupProvider = getProviderFactory().provider(coordinates::getGroupId);
        Provider<String> artifactIdProvider = getProviderFactory().provider(coordinates::getArtifactId);
        Provider<String> versionProvider = getProviderFactory().provider(coordinates::getVersion);
        publication.getPom().withXml(new Action<XmlProvider>() {
            @Override
            public void execute(XmlProvider xmlProvider) {
                Element root = xmlProvider.asElement();
                Document document = root.getOwnerDocument();
                Node dependencies = root.appendChild(document.createElement("dependencies"));
                Node dependency = dependencies.appendChild(document.createElement("dependency"));
                Node groupId = dependency.appendChild(document.createElement("groupId"));
                groupId.setTextContent(groupProvider.get());
                Node artifactId = dependency.appendChild(document.createElement("artifactId"));
                artifactId.setTextContent(artifactIdProvider.get());
                Node version = dependency.appendChild(document.createElement("version"));
                version.setTextContent(versionProvider.get());
            }
        });
        publication.getPom().getName().set(declaration.getDisplayName());
        publication.getPom().getDescription().set(declaration.getDescription());
    }
}
