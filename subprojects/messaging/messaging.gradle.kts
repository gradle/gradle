import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))

    implementation(library("fastutil"))
    implementation(library("kryo"))

    testImplementation(testFixtures(project(":core")))

    testFixturesImplementation(project(":baseServices"))
    integTestRuntimeOnly(project(":runtimeApiInfo"))
}

gradlebuildJava {
    moduleType = ModuleType.WORKER
}
