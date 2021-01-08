plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13")
}

// tag::define-main[]
sourceSets {
    main {
        java {
            setSrcDirs(listOf("src/java"))
        }
        resources {
            setSrcDirs(listOf("src/resources"))
        }
    }
// end::define-main[]
    test {
        java {
            srcDir("test/java")
        }
        resources {
            srcDir("test/resources")
        }
    }
// tag::define-main[]
}
// end::define-main[]
