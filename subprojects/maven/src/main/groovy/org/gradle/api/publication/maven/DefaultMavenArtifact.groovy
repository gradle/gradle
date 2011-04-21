package org.gradle.api.publication.maven

class DefaultMavenArtifact implements MavenArtifact {
    String classifier
    String extension
    File file
}
