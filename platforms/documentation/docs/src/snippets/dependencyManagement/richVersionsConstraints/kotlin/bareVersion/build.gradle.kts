plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::bare-version-constraint[]
dependencies {
    // Brings in commons-codec:1.10
    implementation("org.apache.httpcomponents:httpclient:4.5.4")

    constraints {
        // This does NOT prevent commons-codec from resolving to 1.10.
        // The version "1.9" is treated as "require 1.9", which allows upgrades.
        implementation("commons-codec:commons-codec:1.9")
    }
}

// Result: commons-codec resolves to 1.10 — the constraint is satisfied because 1.10 >= 1.9
// end::bare-version-constraint[]
