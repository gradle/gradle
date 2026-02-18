plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementations for TAPI native models"

dependencies {
    api(projects.ide)
    api(projects.toolingApi)
}
