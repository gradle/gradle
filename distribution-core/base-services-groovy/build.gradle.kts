plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":base-services"))

    implementation(libs.groovy)
    implementation(libs.guava)

    testImplementation(testFixtures(project(":core")))
}

strictCompile {
    ignoreParameterizedVarargType() // [unchecked] Possible heap pollution from parameterized vararg type: org.gradle.api.specs.AndSpec.and()
}
