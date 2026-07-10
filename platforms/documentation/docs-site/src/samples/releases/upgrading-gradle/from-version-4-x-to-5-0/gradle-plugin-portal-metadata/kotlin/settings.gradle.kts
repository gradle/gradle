pluginManagement {
    repositories {
        gradlePluginPortal().apply {
            (this as MavenArtifactRepository).metadataSources {
                mavenPom()
                artifact()
            }
        }
    }
}
