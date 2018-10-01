import org.gradle.gradlebuild.ProjectGroups.publicProjects
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

dependencies {
    api(project(":distributionsDependencies"))
    testImplementation("com.tngtech.archunit:archunit-junit4:0.9.1")
    testImplementation(library("jsr305"))
    publicProjects.forEach {
        testRuntime(it)
    }
}
