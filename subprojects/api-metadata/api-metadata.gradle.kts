import org.gradle.gradlebuild.PublicApi
plugins {
    gradlebuild.distribution.`core-implementation-java`
    gradlebuild.`api-metadata`
}

apiMetadata {
    includes.addAll(PublicApi.includes)
    excludes.addAll(PublicApi.excludes)
}
