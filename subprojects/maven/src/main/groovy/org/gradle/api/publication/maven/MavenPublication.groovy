package org.gradle.api.publication.maven

import org.gradle.api.artifacts.Configuration

interface MavenPublication {
    String getGroupId()
    String getArtifactId()
    String getVersion()
    String getPackaging()
    MavenArtifact getMainArtifact()
    Collection<MavenArtifact> getSubArtifacts()
    Map<MavenScope, Configuration> getDependencies()
    MavenPom getPom()
}