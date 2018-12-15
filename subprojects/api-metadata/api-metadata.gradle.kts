import org.gradle.gradlebuild.ProjectGroups.publicJavaProjects
import org.gradle.gradlebuild.PublicApi
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    gradlebuild.`api-metadata`
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

val gradleApiRuntime by configurations.creating {
    isVisible = false
    isCanBeResolved = true
    isCanBeConsumed = false
    isTransitive = false
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
    attributes.attribute(Attribute.of("org.gradle.api", String::class.java), "metadata")
    exclude(module = "distributions")
}

val gradleApiRuntimeProjects = publicJavaProjects.filter { p ->
    listOf("kotlinDsl", "apiMetadata", "architectureTest", "runtimeApiInfo").all { !p.name.startsWith(it) }
}

dependencies {
    testImplementation(project(":distributionsDependencies"))
    gradleApiRuntimeProjects.forEach {
        gradleApiRuntime(it)
    }
}

apiMetadata {
    sources.from({gradleApiRuntimeProjects.map { it.sourceSets.main.get().allJava }})
    includes.addAll(PublicApi.includes)
    excludes.addAll(PublicApi.excludes)
    classpath.from(gradleApiRuntime)
}
