checkstyle {
    toolVersion = "10.3.3"
}

tasks.withType<Checkstyle>().configureEach {
    reports {
        sarif.required = true
    }
}
