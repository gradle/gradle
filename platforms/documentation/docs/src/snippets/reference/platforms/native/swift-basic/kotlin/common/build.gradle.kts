plugins {
    id("swift-library")
}

// tag::swift-source-set[]
extensions.configure<SwiftLibrary> {
    source.from(file("Sources/Common"))
}
// end::swift-source-set[]
