/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.dependencies.maven.deploy.groovy

import org.gradle.api.dependencies.maven.GroovyMavenUploader
import org.gradle.api.dependencies.maven.MavenPom
import org.gradle.api.dependencies.maven.PublishFilter
import org.gradle.api.internal.dependencies.maven.MavenPomFactory
import org.gradle.api.internal.dependencies.maven.deploy.ArtifactPomContainer
import org.gradle.api.internal.dependencies.maven.deploy.BaseMavenUploader
import org.gradle.util.ConfigureUtil
import org.codehaus.groovy.runtime.InvokerHelper
import org.gradle.api.DependencyManager

/**
 * @author Hans Dockter
 */
class DefaultGroovyMavenUploader extends BaseMavenUploader implements GroovyMavenUploader {
    public static final String REPOSITORY_BUILDER = "repository"
    public static final String SNAPSHOT_REPOSITORY_BUILDER = 'snapshotRepository'
    
    private RepositoryBuilder repositoryBuilder = new RepositoryBuilder()

    DefaultGroovyMavenUploader(String name, ArtifactPomContainer artifactPomContainer, MavenPomFactory mavenPomFactory, DependencyManager dependencyManager) {
        super(name, artifactPomContainer, mavenPomFactory, dependencyManager)
    }

    void filter(Closure filter) {
        this.filter = filter as PublishFilter
    }

    MavenPom addFilter(String name, Closure filter) {
        addFilter(name, filter as PublishFilter)
    }

    MavenPom pom(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, pom)
    }

    MavenPom pom(String name, Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, pom(name))
    }

    def methodMissing(String name, args) {
        if (name == REPOSITORY_BUILDER || name == SNAPSHOT_REPOSITORY_BUILDER) {
            Object repository = InvokerHelper.invokeMethod(repositoryBuilder, REPOSITORY_BUILDER, args)
            if (name == REPOSITORY_BUILDER) {
                setRepository(repository)
            } else {
                setSnapshotRepository(repository)
            }
            return repository;
        } else {
            throw new MissingMethodException(name, this.class, args)
        }
    }


}
