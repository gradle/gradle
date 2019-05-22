import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.`strict-compile`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":files"))
    implementation(project(":processServices"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":baseServicesGroovy"))
    implementation(project(":dependencyManagement"))
    implementation(project(":platformBase"))
    implementation(project(":diagnostics"))

    implementation(library("groovy"))
    implementation(library("guava"))
    implementation(library("commons_lang"))
    implementation(library("commons_io"))
    implementation(library("inject"))
    implementation(library("asm"))

    testImplementation(project(":native"))
    testImplementation(library("ant"))

    integTestImplementation(library("slf4j_api"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
    from(":diagnostics")
    from(":platformBase")
    from(":platformNative")
}
