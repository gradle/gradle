import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `embedded-kotlin`
    `java-library`
}

java {
    gradlebuildJava {
        moduleType = ModuleType.INTERNAL
    }
}

dependencies {
    compile(project(":pluginDevelopment"))
}
