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

// tag::do-this[]
pluginManager.withPlugin("java") {
    extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }
}
// end::do-this[]
