plugins {
    id("cpp-library")
}

// tag::cpp-source-set[]
library {
    source.from(file("src"))
    privateHeaders.from(file("src"))
    publicHeaders.from(file("include"))
}
// end::cpp-source-set[]
