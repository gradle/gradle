val tmpDistDir = layout.buildDirectory.dir("dist")

tasks.register<Jar>("javadocJarArchive") {
    from(tasks.javadoc)  // <1>
    archiveClassifier = "javadoc"
}

tasks.register<Copy>("unpackJavadocs") {
    from(zipTree(tasks.named<Jar>("javadocJarArchive").get().archiveFile))  // <2>
    into(tmpDistDir)  // <3>
}
