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
package org.gradle.api.publication.maven.internal.ant

import org.codehaus.groovy.runtime.InvokerHelper
import org.gradle.api.artifacts.maven.GroovyMavenDeployer
import org.gradle.api.artifacts.maven.PomFilterContainer
import org.gradle.api.publication.maven.internal.ArtifactPomContainer
import org.gradle.logging.LoggingManagerInternal

class DefaultGroovyMavenDeployer extends BaseMavenDeployer implements GroovyMavenDeployer, PomFilterContainer {
    public static final String REPOSITORY_BUILDER = "repository"
    public static final String SNAPSHOT_REPOSITORY_BUILDER = 'snapshotRepository'
    
    private RepositoryBuilder repositoryBuilder = new RepositoryBuilder()

    DefaultGroovyMavenDeployer(PomFilterContainer pomFilterContainer, ArtifactPomContainer artifactPomContainer, LoggingManagerInternal loggingManager) {
        super(pomFilterContainer, artifactPomContainer, loggingManager)
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
