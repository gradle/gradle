plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    api(projects.ide)
    api(projects.toolingApi)
}
