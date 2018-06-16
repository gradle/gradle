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

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.XmlProvider;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublicationContainer;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal;
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension;
import org.gradle.plugin.devel.PluginDeclaration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collections;
import java.util.List;

import static org.gradle.plugin.use.resolve.internal.ArtifactRepositoriesPluginResolver.PLUGIN_MARKER_SUFFIX;

class MavenPluginPublishPlugin implements Plugin<Project> {
    @Override
    public void apply(final Project project) {
        final GradlePluginDevelopmentExtension pluginDevelopment = project.getExtensions().getByType(GradlePluginDevelopmentExtension.class);
        project.getExtensions().configure(PublishingExtension.class, new Action<PublishingExtension>() {
            @Override
            public void execute(final PublishingExtension publishing) {
                // TODO: use named(...)
                SoftwareComponent mainComponent = project.getComponents().getByName("java");
                Provider<Iterable<? extends Publication>> publications = new Monadic().combine(
                    pluginDevelopment.isAutomatedPublishing(),
                    pluginDevelopment.getPlugins().asProvider(),
                    Providers.of(mainComponent),
                    new Transformer<Iterable<? extends Publication>, Monadic.Param3<Boolean, Iterable<PluginDeclaration>, SoftwareComponent>>() {
                        @Override
                        public Iterable<? extends Publication> transform(Monadic.Param3<Boolean, Iterable<PluginDeclaration>, SoftwareComponent> params) {
                            if (params.first) {
                                MavenPublication mainPublication = publishing.getPublications().maybeCreate("pluginMaven", MavenPublication.class);
                                mainPublication.from(params.third);
                                List<Publication> publications = Lists.newArrayList();
                                publications.add(mainPublication);
                                for (PluginDeclaration declaration : params.second) {
                                    publications.add(createMavenMarkerPublication(declaration, mainPublication, publishing.getPublications()));
                                }
                                return publications;
                            }
                            return Collections.emptyList();
                        }
                    });
                publishing.getPublications().addAllLater(publications);
            }
        });
    }

    // Use factory instead of PublicationContainer
    private Publication createMavenMarkerPublication(PluginDeclaration declaration, final MavenPublication coordinates, PublicationContainer publications) {
        String pluginId = declaration.getId();
        MavenPublicationInternal publication = (MavenPublicationInternal) publications.create(declaration.getName() + "PluginMarkerMaven", MavenPublication.class);
        publication.setAlias(true);
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
        return publication;
    }
}
