plugins { id("base") }

repositories {
    mavenCentral() // <1>
}

val codec = configurations.create("codec") { // <2>
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    }
    isVisible = false
    isCanBeConsumed = false
}

dependencies {
    codec("commons-codec:commons-codec:1.10") // <3>
}

tasks.register<CreateMD5>("md5") {
    codecClasspath.from(codec) // <4>
    destinationDirectory.set(project.layout.buildDirectory.dir("md5"))
    source(project.layout.projectDirectory.file("src"))
}
