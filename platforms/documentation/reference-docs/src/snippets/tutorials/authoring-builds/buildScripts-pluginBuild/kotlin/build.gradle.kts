// tag::plugin-build-script[]
plugins {                                                             // <1>
    `java-gradle-plugin`                                              // <2>
    id("org.jetbrains.kotlin.jvm") version "2.2.0-RC2"                  // <3>
}

repositories {                                                        // <4>
    mavenCentral()                                                    // <5>
}

dependencies {                                                        // <6>
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")     // <7>
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {                                                        // <8>
    plugins.create("greeting") {                                      // <9>
        id = "license.greeting"
        implementationClass = "license.LicensePlugin"
    }
}

// Additional lines //
// end::plugin-build-script[]
