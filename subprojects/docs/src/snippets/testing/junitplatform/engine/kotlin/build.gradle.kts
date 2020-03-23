plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testCompileOnly("junit:junit:4.12")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
}

// tag::filter-engine[]
tasks.test {
    useJUnitPlatform {
        includeEngines("junit-vintage")
        // excludeEngines("junit-jupiter")
    }
}
// end::filter-engine[]
