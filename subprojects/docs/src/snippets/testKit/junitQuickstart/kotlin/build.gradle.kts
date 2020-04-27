plugins {
    `kotlin-dsl`
}

// tag::declare-gradle-testkit-dependency[]
dependencies {
    testImplementation(gradleTestKit())
}
// end::declare-gradle-testkit-dependency[]

// tag::declare-junit-dependency[]
dependencies {
    testImplementation("junit:junit:4.12")
}
// end::declare-junit-dependency[]

repositories {
    mavenCentral()
}
