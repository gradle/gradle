import org.myorg.LatestArtifactVersion

// tag::plugin-extension[]
plugins {
    id("org.myorg.binary-repository-version")
}

binaryRepo {
    coordinates.set("commons-lang:commons-lang")
    serverUrl.set("http://repo2.myorg.org/maven2")
}
// end::plugin-extension[]

// tag::direct-task-register[]
tasks.register<LatestArtifactVersion>("latestVersionMavenCentral") {
    coordinates.set("commons-lang:commons-lang")
    serverUrl.set("http://repo1.maven.org/maven2")
}

tasks.register<LatestArtifactVersion>("latestVersionInhouseRepo") {
    coordinates.set("commons-lang:commons-lang")
    serverUrl.set("http://repo1.myorg.org/maven2")
}
// end::direct-task-register[]
