package org.gradle.api.publication.maven

interface MavenPublication {
    String getGroupId()
    String getArtifactId()
    String getVersion()
    MavenArtifact getMainArtifact()
    Collection<MavenArtifact> getSubArtifacts()
}