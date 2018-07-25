import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    id("gradlebuild.classycle")
}

dependencies {
    api(project(":cli"))
    api(library("jsr305"))
    implementation("commons-lang:commons-lang:2.6")
}

gradlebuildJava {
    moduleType = ModuleType.ENTRY_POINT
}
