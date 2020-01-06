import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":core"))
    implementation(project(":coreApi"))
    implementation(project(":logging"))
}

gradlebuildJava {
    moduleType = ModuleType.STARTUP
}
