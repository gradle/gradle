import org.myorg.LatestArtifactVersion

// tag::plugin-extension[]
plugins {
    id("org.myorg.binary-repository-version")
}

binaryRepo {
    coordinates = "commons-lang:commons-lang"
    serverUrl = "http://repo2.myorg.org/maven2"
}
// end::plugin-extension[]

// tag::direct-task-register[]
tasks.register<LatestArtifactVersion>("latestVersionMavenCentral") {
    coordinates = "commons-lang:commons-lang"
    serverUrl = "http://repo1.maven.org/maven2"
}

tasks.register<LatestArtifactVersion>("latestVersionInhouseRepo") {
    coordinates = "commons-lang:commons-lang"
    serverUrl = "http://repo1.myorg.org/maven2"
}
// end::direct-task-register[]
