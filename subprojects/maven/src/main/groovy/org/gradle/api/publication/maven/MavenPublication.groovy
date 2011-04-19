package org.gradle.api.publication.maven

interface MavenPublication {
    String getGroupId()
    String getArtifactId()
    String getVersion()
    String getClassifier()
    Set<MavenArtifact> getArtifacts()
}