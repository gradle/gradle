package org.gradle.api.publication.maven

class DefaultMavenPublication implements MavenPublication {
    String groupId
    String artifactId
    String version
    String packaging
    MavenArtifact mainArtifact
    Set<MavenArtifact> subArtifacts = []
    Set<MavenDependency> dependencies = []
    MavenPom pom
}
