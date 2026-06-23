plugins {
    `java-library`
}

// tag::use-multiple-catalogs[]
dependencies {
    implementation(libs.guava)
    implementation(libs.commons.lang3)

    implementation(tools.errorprone.annotations)
    implementation(tools.jsr305)

    testImplementation(testLibs.junit.api)
    testImplementation(testLibs.mockito)
}
// end::use-multiple-catalogs[]
