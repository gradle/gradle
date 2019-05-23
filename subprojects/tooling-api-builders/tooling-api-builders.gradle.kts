import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.`strict-compile`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":processServices"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":baseServicesGroovy")) // for 'Specs'
    implementation(project(":testingBase"))
    implementation(project(":testingJvm"))
    implementation(project(":launcher"))
    implementation(project(":dependencyManagement"))
    implementation(project(":reporting"))
    implementation(project(":workers"))
    implementation(project(":compositeBuilds"))
    implementation(project(":toolingApi"))

    implementation(library("groovy")) // for 'Closure'
    implementation(library("guava"))
    implementation(library("commons_io"))

    testImplementation(project(":files"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

strictCompile {
    ignoreDeprecations = true
}
