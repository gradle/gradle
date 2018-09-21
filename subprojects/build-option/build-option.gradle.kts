import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    id("gradlebuild.classycle")
}

dependencies {
    api(project(":cli"))
    api(library("jsr305"))
    implementation(library("commons_lang"))
}

gradlebuildJava {
    moduleType = ModuleType.ENTRY_POINT
}
