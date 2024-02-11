plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenCentral()
}

// tag::consumer_dependencies[]
dependencies {
    implementation(project(":lib"))

    testImplementation("junit:junit:4.13")
    testImplementation(testFixtures(project(":lib")))
}
// end::consumer_dependencies[]

val functionalTest by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = false
}
val functionalTestClasspath by configurations.creating {
    extendsFrom(functionalTest)
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_API))
    }
}

// tag::external-test-fixtures-dependency[]
dependencies {
    // Adds a dependency on the test fixtures of Gson, however this
    // project doesn't publish such a thing
    functionalTest(testFixtures("com.google.code.gson:gson:2.8.5"))
}
// end::external-test-fixtures-dependency[]
