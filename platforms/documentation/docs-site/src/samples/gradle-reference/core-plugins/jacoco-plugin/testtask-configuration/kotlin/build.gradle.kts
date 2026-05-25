tasks.test {
    extensions.configure(JacocoTaskExtension::class) {
        destinationFile = layout.buildDirectory.file("jacoco/jacocoTest.exec").get().asFile
        classDumpDir = layout.buildDirectory.dir("jacoco/classpathdumps").get().asFile
    }
}
