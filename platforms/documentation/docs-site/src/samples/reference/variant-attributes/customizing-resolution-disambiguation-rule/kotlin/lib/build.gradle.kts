plugins {
    `base`
}

repositories {
    mavenCentral()
}

val kindAttr = Attribute.of("kind", String::class.java)

val v1Jar = tasks.register<Jar>("v1Jar") {
    archiveBaseName.set("v1")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    from("src/v1")
}

val v2Jar = tasks.register<Jar>("v2Jar") {
    archiveBaseName.set("v2")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    from("src/v2")
}

val v1 = configurations.create("v1") {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
        attribute(kindAttr, "api")
    }
    outgoing.artifact(v1Jar)
}

val v2 = configurations.create("v2") {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
        attribute(kindAttr, "secret api")
    }
    outgoing.artifact(v2Jar)
}
