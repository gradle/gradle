import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":messaging"))
    implementation(project(":native"))
    implementation(project(":processServices"))

    implementation(library("slf4j_api"))

    testImplementation(testFixtures(project(":core")))
}

gradlebuildJava {
    moduleType = ModuleType.WORKER
}
