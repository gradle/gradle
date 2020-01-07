import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":cli"))

    implementation(project(":baseAnnotations"))
    implementation(library("commons_lang"))
}

gradlebuildJava {
    moduleType = ModuleType.WORKER
}
