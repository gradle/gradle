import org.gradle.gradlebuild.ProjectGroups.javaProjects
import org.gradle.gradlebuild.PublicApi
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    id("gradlebuild.api-metadata")
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

apiMetadata {
    sources.from(provider {
        javaProjects.map { it.sourceSets["main"].allJava }
    })
    includes.addAll(PublicApi.includes)
    excludes.addAll(PublicApi.excludes)
    classpath.from(rootProject.configurations.runtime)
    classpath.from(rootProject.configurations["gradlePlugins"])
}
