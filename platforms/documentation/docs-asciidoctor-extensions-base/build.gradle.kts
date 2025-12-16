import gradlebuild.basics.classanalysis.Attributes.minified

plugins {
    id("gradlebuild.internal.java")
    groovy
}

description = "Asciidoctor extensions that work with all backends"

val asciiDoctorVersion = "2.5.13"

dependencies {
    api("org.asciidoctor:asciidoctorj-api:$asciiDoctorVersion")
    api("org.asciidoctor:asciidoctorj:$asciiDoctorVersion")
    api("org.jspecify:jspecify:1.0.0")

    implementation("commons-io:commons-io:2.11.0")
    testImplementation("org.spockframework:spock-core")
}

// ascii-doctor depends on JRuby, which has a dependency on jnr-constants, unit tests fail because of that, but this shouldn't happen when it's used as a plugin due to classloader isolation
configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module("com.github.jnr:jnr-constants"))
            .using(variant(module("com.github.jnr:jnr-constants:0.10.4")) {
                attributes {
                    attribute(minified, false)
                }
            })
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

errorprone {
    nullawayEnabled = true
}
