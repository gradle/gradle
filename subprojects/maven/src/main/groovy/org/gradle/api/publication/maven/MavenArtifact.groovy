package org.gradle.api.publication.maven

interface MavenArtifact {
    String getClassifier()
    String getExtension()
    File getFile()
}
