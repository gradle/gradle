plugins {
    java
}

// tag::toolchain-known-vendor[]
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
        vendor.set(JvmVendorSpec.ADOPTIUM)
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


// tag::toolchain-matching-implementation[]
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
        vendor.set(JvmVendorSpec.IBM_SEMERU)
        implementation.set(JvmImplementation.J9)
    }
}
// end::toolchain-matching-implementation[]
