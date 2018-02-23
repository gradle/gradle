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

package org.gradle.api.publication.maven.internal.deployer;

import groovy.lang.Closure;
import org.apache.maven.artifact.ant.RemoteRepository;
import org.gradle.api.artifacts.maven.GroovyMavenDeployer;
import org.gradle.api.artifacts.maven.PomFilterContainer;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider;
import org.gradle.api.publication.maven.internal.ArtifactPomContainer;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.util.ConfigureUtil;

import java.util.Map;

public class DefaultGroovyMavenDeployer extends BaseMavenDeployer implements GroovyMavenDeployer, PomFilterContainer {

    public DefaultGroovyMavenDeployer(PomFilterContainer pomFilterContainer, ArtifactPomContainer artifactPomContainer, LoggingManagerInternal loggingManager, MavenSettingsProvider mavenSettingsProvider, LocalMavenRepositoryLocator mavenRepositoryLocator) {
        super(pomFilterContainer, artifactPomContainer, loggingManager, mavenSettingsProvider, mavenRepositoryLocator);
    }

    public RemoteRepository repository(Map properties) {
        RemoteRepository repository = createRepository(properties);
        setRepository(repository);
        return repository;
    }

    public RemoteRepository repository(Map properties, Closure closure) {
        RemoteRepository repository = createRepository(properties, closure);
        setRepository(repository);
        return repository;
    }

    public RemoteRepository snapshotRepository(Map properties) {
        RemoteRepository repository = createRepository(properties);
        setSnapshotRepository(repository);
        return repository;
    }

    public RemoteRepository snapshotRepository(Map properties, Closure closure) {
        RemoteRepository repository = createRepository(properties, closure);
        setSnapshotRepository(repository);
        return repository;
    }

    private RemoteRepository createRepository(Map properties) {
        RemoteRepository repository = new MavenRemoteRepository();
        ConfigureUtil.configureByMap(properties, repository);
        return repository;
    }

    private RemoteRepository createRepository(Map properties, Closure closure) {
        RemoteRepository repository = new MavenRemoteRepository();
        ConfigureUtil.configureByMap(properties, repository);
        // Should be using ConfigureUtil (with DELEGATE_FIRST strategy), however for backwards compatibility need to use OWNER_FIRST
        new ClosureBackedAction<RemoteRepository>(closure, Closure.OWNER_FIRST).execute(repository);
        return repository;
    }
}
