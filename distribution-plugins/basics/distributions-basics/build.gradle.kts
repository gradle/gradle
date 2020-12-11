plugins {
    id("gradlebuild.distribution.packaging")
}

dependencies {
    coreRuntimeOnly(platform("org.gradle:core-platform"))

    pluginsRuntimeOnly(platform("org.gradle:distributions-core"))

    pluginsRuntimeOnly(project(":resources-sftp"))
    pluginsRuntimeOnly(project(":resources-s3"))
    pluginsRuntimeOnly(project(":resources-gcs"))
    pluginsRuntimeOnly(project(":build-cache-http"))

    pluginsRuntimeOnly(project(":kotlin-dsl-tooling-builders"))

    pluginsRuntimeOnly(project(":test-kit"))
}
