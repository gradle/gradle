package org.gradle.api.publication.maven

import org.gradle.api.artifacts.Configuration

class DefaultMavenPublication implements MavenPublication {
    String groupId
    String artifactId
    String version
    String packaging
    MavenArtifact mainArtifact
    Collection<MavenArtifact> subArtifacts = []
    Map<MavenScope, Configuration> dependencies = [:]
    MavenPom pom
}
