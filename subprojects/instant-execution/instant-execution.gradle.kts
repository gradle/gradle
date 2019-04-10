import build.futureKotlin
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `kotlin-library`
}

dependencies {
    implementation(project(":core"))
    implementation(project(":modelCore"))
    implementation(project(":files"))

    compile(futureKotlin("stdlib-jdk8"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

