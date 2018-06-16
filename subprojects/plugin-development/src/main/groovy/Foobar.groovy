import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.provider.Providers
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
/*
 * Copyright 2018 the original author or authors.
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

class Foobar implements Plugin<Project> {
    @Override
    public void apply(final Project project) {
        final pluginDevelopment = project.extensions.pluginDevelopment

        project.extensions.configure(PublishingExtension) {
            // TODO: use named(...)
            def inputs = combine(pluginDevelopment.isAutomatedPublishing(),
                pluginDevelopment.getPlugins().asProvider(),
                Providers.of(project.components.java))

            def publications = inputs.map { (isAutomatedPublishing, plugins, mainComponent) ->
                if (isAutomatedPublishing) {
                    MavenPublication mainPublication = publishing.publications.maybeCreate("pluginMaven", MavenPublication)
                    mainPublication.from(mainComponent)
                    return plugins.inject([mainPublication]) { declaration ->
                        createMavenMarkerPublication(declaration, mainPublication, publishing.getPublications())
                    }
                }
                return Collections.emptyList()
            }
            publishing.getPublications().addAllLater(publications);
        }
    }
}
