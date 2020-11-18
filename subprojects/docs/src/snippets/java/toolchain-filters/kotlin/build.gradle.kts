plugins {
    java
}

// tag::toolchain-known-vendor[]
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
        vendor.set(JvmVendorSpec.ADOPTOPENJDK)
    }
}
// end::toolchain-known-vendor[]


// tag::toolchain-matching-vendor[]
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
        vendor.set(JvmVendorSpec.matching("customString"))
    }
}
// end::toolchain-matching-vendor[]
