import org.gradle.gradlebuild.ProjectGroups.javaProjects
import org.gradle.gradlebuild.PublicApi

plugins {
    id("gradlebuild.api-metadata")
}

apiMetadata {
    sources.from(provider {
        javaProjects.map { it.sourceSets["main"].allJava }
    })
    includes.addAll(provider { PublicApi.includes })
    excludes.addAll(provider { PublicApi.excludes })
    classpath.from(rootProject.configurations.runtime)
    classpath.from(rootProject.configurations["gradlePlugins"])
}

dependencies {
    testImplementation(project(":distributionsDependencies"))
}
