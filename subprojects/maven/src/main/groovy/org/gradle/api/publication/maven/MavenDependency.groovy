package org.gradle.api.publication.maven

interface MavenDependency {
    String groupId
    String artifactId
    String classifier
    MavenScope scope
    boolean optional
}
