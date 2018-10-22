import org.gradle.gradlebuild.ProjectGroups.javaProjects
import org.gradle.gradlebuild.PublicApi
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    gradlebuild.`api-metadata`
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

configurations {
    create("gradleApiRuntime") {
        isVisible = false
        isCanBeResolved = true
        isCanBeConsumed = false
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
        attributes.attribute(Attribute.of("org.gradle.api", String::class.java), "metadata")
    }
}
dependencies {
    testImplementation(project(":distributionsDependencies"))
    "gradleApiRuntime"(project(":"))
}

apiMetadata {
    sources.from(javaProjects.map { it.sourceSets.main.get().allJava })
    includes.addAll(PublicApi.includes)
    excludes.addAll(PublicApi.excludes)
    classpath.from(configurations["gradleApiRuntime"])
}
