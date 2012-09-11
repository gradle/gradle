/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.github

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.artifacts.ResolverFactory
import org.gradle.api.internal.artifacts.repositories.DefaultPasswordCredentials
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.plugins.github.internal.DefaultGitHubDownloadsRepository
import org.gradle.internal.Factory
import org.gradle.internal.reflect.Instantiator

import javax.inject.Inject
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.Incubating

@Incubating
class GitHubDependenciesPlugin implements Plugin<Project> {

    FileResolver fileResolver
    Instantiator instantiator
    ResolverFactory resolverFactory

    @Inject
    GitHubDependenciesPlugin(FileResolver fileResolver, Instantiator instantiator, DependencyResolutionServices dependencyResolutionServices) {
        this.fileResolver = fileResolver
        this.instantiator = instantiator
        this.resolverFactory = dependencyResolutionServices.resolverFactory
    }

    void apply(Project project) {
        Factory<MavenArtifactRepository> mavenRepostoryFactory = new Factory<MavenArtifactRepository>() {
            MavenArtifactRepository create() {
                resolverFactory.createMavenRepository()
            }
        }

        Factory<GitHubDownloadsRepository> downloadsRepositoryFactory = new Factory<GitHubDownloadsRepository>() {
            GitHubDownloadsRepository create() {
                def passwordCredentials = instantiator.newInstance(DefaultPasswordCredentials)
                instantiator.newInstance(DefaultGitHubDownloadsRepository, fileResolver, passwordCredentials, mavenRepostoryFactory)
            }
        }

        project.repositories.extensions.create("github", GitHubRepositoryHandlerExtension, project.repositories, downloadsRepositoryFactory)
    }
}
