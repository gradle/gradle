import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.`strict-compile`
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":dependencyManagement"))
    implementation(project(":workers"))
    implementation(project(":execution"))

    implementation(library("groovy"))
    implementation(library("guava"))
    implementation(library("commons_lang"))

    testFixturesImplementation(project(":files"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
    from(":coreApi")
    from(":core", "testFixtures")
    from(":modelCore", "testFixtures")
    from(":diagnostics", "testFixtures")
}
