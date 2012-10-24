/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.publication

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.publication.maven.internal.modelbuilder.MavenPublicationBuilder
import org.gradle.internal.reflect.Instantiator

import javax.inject.Inject

/**
 * This is only temporary plugin :) When we're happy with what it does we can move that to the core dsl?
 *
 * @author: Szczepan Faber, created at: 6/16/11
 * @deprecated Replaced by the new publications model.
 */
@Deprecated
class PublicationPlugin implements Plugin<Project> {
    private final Instantiator instantiator

    @Inject
    PublicationPlugin(Instantiator instantiator) {
        this.instantiator = instantiator
    }

    void apply(Project project) {
        def newPublications = project.extensions.create("publications", Publications)

        project.plugins.withType(MavenPlugin) {
            newPublications.maven = new MavenPublicationBuilder(instantiator).build(project)
            project.task("publishArchives", dependsOn: 'assemble', type: PublishPublications.class) {
                publications = newPublications
            }
            project.task("installArchives", dependsOn: 'assemble', type: InstallPublications.class) {
                publications = newPublications
            }
        }
    }
}
