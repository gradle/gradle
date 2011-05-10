package org.gradle.api.publication.maven

interface MavenPom {
    void apply(Closure pomBuilder)
    void whenConfigured(Closure modelTransformer)
    void withXml(Closure xmlBuilder)
}
