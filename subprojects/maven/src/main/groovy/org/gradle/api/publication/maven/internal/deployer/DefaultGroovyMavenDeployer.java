package org.gradle.api.publication.maven.internal.deployer;

import groovy.lang.Closure;
import org.apache.maven.artifact.ant.RemoteRepository;
import org.gradle.api.artifacts.maven.GroovyMavenDeployer;
import org.gradle.api.artifacts.maven.PomFilterContainer;
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
        ConfigureUtil.configure(closure, repository);
        return repository;
    }
}
