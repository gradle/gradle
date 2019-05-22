import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":cli"))

    implementation(library("jsr305"))
    implementation(library("commons_lang"))
}

gradlebuildJava {
    moduleType = ModuleType.WORKER
}
