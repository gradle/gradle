plugins {
    id("java-library")

    //alias(libs.plugins.jacocolog) // Unfortunately it is not possible the version catalog in buildSrc code for plugins

    // Remember that unlike regular Gradle projects, convention plugins in buildSrc do not automatically resolve
    // external plugins. We must declare them as dependencies in buildSrc/build.gradle.kts.
    id("org.barfuin.gradle.jacocolog") // Apply the plugin manually as a workaround with the external plugin
                                       // version from the version catalog specified in implementation dependency
                                       // artifact in build file
}

repositories {
    mavenCentral()
}

// Access the version catalog
val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
//val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    // Access version catalog in buildSrc code for dependencies
    implementation(libs.findLibrary("guava").get()) // Regular library from version catalog
    testImplementation(platform("org.junit:junit-bom:5.9.1")) // Platform dependency
    testImplementation("org.junit.jupiter:junit-jupiter") // Direct dependency
}

tasks.test {
    useJUnitPlatform()
}
