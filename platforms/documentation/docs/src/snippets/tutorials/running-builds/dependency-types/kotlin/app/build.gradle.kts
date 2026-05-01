plugins {
    id("java")
}

repositories {
    mavenCentral()
}

// tag::dep-types[]
dependencies {
    implementation("com.google.guava:guava:32.1.2-jre")             // <1>
    implementation(project(":utils"))                               // <2>
    runtimeOnly(files("some.jar"))                                  // <3>
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")    // <4>
}
// end::dep-types[]
