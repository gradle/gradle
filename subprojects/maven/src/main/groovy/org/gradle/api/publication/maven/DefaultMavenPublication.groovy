package org.gradle.api.publication.maven


class DefaultMavenPublication implements MavenPublication {
    String groupId
    String artifactId
    String version
    MavenArtifact mainArtifact
    Collection<MavenArtifact> subArtifacts = []
}
