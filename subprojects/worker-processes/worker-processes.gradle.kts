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

    testImplementation(testFixtures(project(":core")))
}

gradlebuildJava {
    moduleType = ModuleType.WORKER
}
