plugins {
    id("org.myorg.url-verifier")        // <1>
}

verification {
    url.set("https://www.google.com/")  // <2>
}
