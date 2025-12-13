plugins {
    id("application")
}

repositories {
    mavenCentral()
}

// tag::dependency-full[]
dependencies {
    implementation("org.apache.httpcomponents:httpclient") // No version specified
    constraints {
        implementation("org.apache.httpcomponents:httpclient:4.5.3") {
            because("previous versions have a bug impacting this application")
        }
        implementation("commons-codec:commons-codec:1.11") {
            because("version 1.9 pulled from httpclient has bugs affecting this application")
        }
    }
}
// end::dependency-full[]
