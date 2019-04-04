import build.futureKotlin
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `kotlin-library`
}

dependencies {
    api(project(":coreApi"))

    compile(futureKotlin("stdlib-jdk8"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

