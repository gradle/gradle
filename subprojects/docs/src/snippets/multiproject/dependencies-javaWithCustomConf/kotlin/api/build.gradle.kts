plugins {
    id("myproject.java-conventions")
}

dependencies {
    implementation(project(":shared"))
}
val spiJar = tasks.register<Jar>("spiJar") {
    archiveBaseName.set("api-spi")
    from(sourceSets.main.get().output)
    include("org/gradle/sample/api/**")
}
configurations {
    create("spi") {
        outgoing.artifact(spiJar)
    }
}
