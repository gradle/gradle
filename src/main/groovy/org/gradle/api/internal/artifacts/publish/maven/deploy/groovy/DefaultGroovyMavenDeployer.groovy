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
package org.gradle.api.internal.artifacts.publish.maven.deploy.groovy

import org.codehaus.groovy.runtime.InvokerHelper
import org.gradle.api.artifacts.maven.GroovyMavenDeployer
import org.gradle.api.artifacts.maven.GroovyPomFilterContainer
import org.gradle.api.artifacts.maven.MavenPom
import org.gradle.api.internal.artifacts.ConfigurationContainer
import org.gradle.api.internal.artifacts.publish.maven.deploy.ArtifactPomContainer
import org.gradle.api.internal.artifacts.publish.maven.deploy.BaseMavenDeployer
import org.gradle.api.internal.artifacts.publish.maven.deploy.groovy.RepositoryBuilder

/**
 * @author Hans Dockter
 */
class DefaultGroovyMavenDeployer extends BaseMavenDeployer implements GroovyMavenDeployer, GroovyPomFilterContainer {
    public static final String REPOSITORY_BUILDER = "repository"
    public static final String SNAPSHOT_REPOSITORY_BUILDER = 'snapshotRepository'
    
    private RepositoryBuilder repositoryBuilder = new RepositoryBuilder()

    DefaultGroovyMavenDeployer(String name, GroovyPomFilterContainer pomFilterContainer, ArtifactPomContainer artifactPomContainer, ConfigurationContainer configurationContainer) {
        super(name, pomFilterContainer, artifactPomContainer, configurationContainer)
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

    void filter(Closure filter) {
        getPomFilterContainer().filter(filter)
    }

    MavenPom addFilter(String name, Closure filter) {
        getPomFilterContainer().addFilter(name, filter)
    }

    MavenPom pom(Closure configureClosure) {
        getPomFilterContainer().pom(configureClosure)
    }

    MavenPom pom(String name, Closure configureClosure) {
        getPomFilterContainer().pom(name, configureClosure)
    }

}
