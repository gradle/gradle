package org.gradle.api.publication.maven

interface MavenPublication {
    String getGroupId()
    String getArtifactId()
    String getVersion()
    String getPackaging()
    MavenArtifact getMainArtifact()
    Set<MavenArtifact> getSubArtifacts()
    Set<MavenDependency> getDependencies()
    MavenPom getPom()
}