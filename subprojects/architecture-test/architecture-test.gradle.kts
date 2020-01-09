import org.gradle.gradlebuild.ProjectGroups.publicProjects
import org.gradle.gradlebuild.PublicApi
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

dependencies {
    testImplementation(project(":baseServices"))
    testImplementation(project(":modelCore"))

    testImplementation(testLibrary("archunit_junit4"))
    testImplementation(library("guava"))

    publicProjects.forEach {
        testRuntimeOnly(it)
    }
}

tasks.withType<Test> {
    // Looks like loading all the classes requires more than the default 512M
    maxHeapSize = "700M"

    systemProperty("org.gradle.public.api.includes", PublicApi.includes.joinToString(":"))
    systemProperty("org.gradle.public.api.excludes", PublicApi.excludes.joinToString(":"))
}
