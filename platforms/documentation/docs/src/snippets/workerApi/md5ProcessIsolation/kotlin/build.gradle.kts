plugins { id("base") }

repositories {
    mavenCentral()
}

val codec = configurations.register("codec") {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    }
    isCanBeConsumed = false
}

dependencies {
    codec("commons-codec:commons-codec:1.10")
}

tasks.register<CreateMD5>("md5") {
    codecClasspath.from(codec)
    destinationDirectory = project.layout.buildDirectory.dir("md5")
    source(project.layout.projectDirectory.file("src"))
}
