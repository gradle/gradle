import org.gradle.gradlebuild.ProjectGroups.publicJavaProjects
import org.gradle.gradlebuild.PublicApi
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    gradlebuild.`api-metadata`
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

apiMetadata {
    sources.from({ publicJavaProjects.map { it.sourceSets.main.get().allJava } })
    includes.addAll(PublicApi.includes)
    excludes.addAll(PublicApi.excludes)
}

dependencies {
    testImplementation(project(":distributionsDependencies"))
}
