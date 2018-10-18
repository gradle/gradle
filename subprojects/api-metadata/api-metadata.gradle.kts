import org.gradle.gradlebuild.ProjectGroups.javaProjects
import org.gradle.gradlebuild.PublicApi
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    id("gradlebuild.api-metadata")
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

val apiMetadata by configurations.creating {
    isVisible = false
    isCanBeConsumed = false
    isCanBeResolved = true
    description = "Classpath configuration for API metadata"
    attributes.attribute(Attribute.of("org.gradle.api.metadata", String::class.java), "yes")
}

apiMetadata {
    sources.from(provider {
        javaProjects.map { it.sourceSets["main"].allJava }
    })
    includes.addAll(provider { PublicApi.includes })
    excludes.addAll(provider { PublicApi.excludes })
    classpath.from(apiMetadata)
}

dependencies {
    testImplementation(project(":distributionsDependencies"))
}
