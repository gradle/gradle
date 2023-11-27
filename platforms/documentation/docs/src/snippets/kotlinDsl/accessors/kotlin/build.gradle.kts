// tag::accessors[]
plugins {
    `java-library`
}

dependencies {                              // <1>
    api("junit:junit:4.13")
    implementation("junit:junit:4.13")
    testImplementation("junit:junit:4.13")
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
        useJUnit()
    }
}
// end::accessors[]

repositories {
    mavenCentral()
}
