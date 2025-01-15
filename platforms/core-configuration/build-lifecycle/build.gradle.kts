plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Services and state containers for build, settings and projects"

dependencies {
    api(projects.baseServices)
    api(projects.fileCollections)
    api(projects.modelCore)
    api(projects.processServices)
}
