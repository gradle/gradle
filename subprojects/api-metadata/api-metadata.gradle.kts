import org.gradle.gradlebuild.PublicApi
plugins {
    gradlebuild.internal.java
    gradlebuild.`api-metadata`
}

apiMetadata {
    includes.addAll(PublicApi.includes)
    excludes.addAll(PublicApi.excludes)
}
