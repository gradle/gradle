plugins {
    `java-library`
}

// tag::avoid-this[]
dependencies {
    implementation("org.apache.commons:commons-pool2:2.12.1") // <1>
    implementation("org.hibernate:hibernate-core:3.6.10.Final")
    // ... other dependencies ...
}

configurations {
    "implementation" {
        exclude(group = "cglib") // <2>
    }

    "implementation" {
        exclude(group = "org.ow2.asm", module = "asm-util") // <3>
    }
}

configurations.configureEach {
    exclude(group = "javassist", module = "javassist") // <4>
}
// end::avoid-this[]
