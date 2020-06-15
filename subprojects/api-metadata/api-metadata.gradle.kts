import org.gradle.gradlebuild.PublicApi
plugins {
    gradlebuild.distribution.`implementation-java`
    gradlebuild.`api-metadata`
}

apiMetadata {
    includes.addAll(PublicApi.includes)
    excludes.addAll(PublicApi.excludes)
}
