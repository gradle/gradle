tasks.register<Copy>("installExecutable") {
    from("build/my-binary")
    into("/usr/local/bin")
    ignoreExistingContentInDestinationDir()
}
