// tag::avoid-this[]
plugins {
    id("myplugin")
}
// end::avoid-this[]

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

// tag::avoid-this[]
// Assumes 'java' plugin is present
extensions.getByType<org.gradle.api.plugins.JavaPluginExtension>().apply {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
// end::avoid-this[]
