// tag::accessors[]
plugins {
    `java-library`
}

dependencies {                              // <1>
    api("junit:junit:4.12")
    implementation("junit:junit:4.12")
    testImplementation("junit:junit:4.12")
}

configurations {                            // <1>
    implementation {
        resolutionStrategy.failOnVersionConflict()
    }
}

sourceSets {                                // <2>
    main {                                  // <3>
        java.srcDir("src/core/java")
    }
}

java {                                      // <4>
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks {
    test {                                  // <5>
        testLogging.showExceptions = true
    }
}
// end::accessors[]

repositories {
    jcenter()
}
