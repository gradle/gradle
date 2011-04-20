package org.gradle.api.publication.maven

public interface MavenPublisher {
    void install(MavenPublication publication)
    void deploy(MavenPublication publication, MavenRepository repository)
}