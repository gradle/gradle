// tag::no-accessors[]
// tag::dependencies[]
// tag::project-extension[]
// tag::tasks[]
// tag::project-container-extension[]
apply(plugin = "java-library")

// end::project-extension[]
// end::tasks[]
// end::project-container-extension[]
dependencies {
    "api"("junit:junit:4.13")
    "implementation"("junit:junit:4.13")
    "testImplementation"("junit:junit:4.13")
}

configurations {
    "implementation" {
        resolutionStrategy.failOnVersionConflict()
    }
}
// end::dependencies[]

// tag::project-extension[]
// tag::project-container-extension[]
configure<SourceSetContainer> {
    named("main") {
        java.srcDir("src/core/java")
    }
}
// end::project-container-extension[]

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
// end::project-extension[]

// tag::tasks[]
tasks {
    named<Test>("test") {
        testLogging.showExceptions = true
    }
}
// end::tasks[]
// end::no-accessors[]

repositories {
    mavenCentral()
}
