import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    
    implementation(library("fastutil"))
    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("kryo"))

    testImplementation(testFixtures(project(":core")))

    testFixturesImplementation(project(":baseServices"))
    testFixturesImplementation(library("slf4j_api"))
    
    integTestRuntimeOnly(project(":runtimeApiInfo"))
}

gradlebuildJava {
    moduleType = ModuleType.WORKER
}
