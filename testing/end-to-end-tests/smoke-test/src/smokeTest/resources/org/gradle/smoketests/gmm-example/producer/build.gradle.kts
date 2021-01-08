plugins {
    id("com.android.library") version "$androidPluginVersion" apply false
    kotlin("jvm") version "$kotlinVersion" apply false
    kotlin("android") version "$kotlinVersion" apply false
    kotlin("multiplatform") version "$kotlinVersion" apply false
}

subprojects {
    apply(plugin = "maven-publish")

    repositories {
        google()
        mavenCentral()
        /*
        jcenter() requried because:
        > Could not resolve all files for configuration ':android-kotlin-library:lintClassPath'.
           > Could not find org.jetbrains.trove4j:trove4j:20160824.
             Searched in the following locations:
               - https://dl.google.com/dl/android/maven2/org/jetbrains/trove4j/trove4j/20160824/trove4j-20160824.pom
               - https://repo.maven.apache.org/maven2/org/jetbrains/trove4j/trove4j/20160824/trove4j-20160824.pom
             Required by:
                 project :android-kotlin-library > com.android.tools.lint:lint-gradle:27.0.2 > com.android.tools:sdk-common:27.0.2
                 project :android-kotlin-library > com.android.tools.lint:lint-gradle:27.0.2 > com.android.tools.external.com-intellij:intellij-core:27.0.2

         Newer versions of this library are available in Maven Central: https://search.maven.org/artifact/org.jetbrains.intellij.deps/trove4j
         */
        jcenter()
    }

    group = "example"
    version = "1.0"

    extensions.getByType<PublishingExtension>().apply {
        repositories {
            maven {
                setUrl(File(rootDir, "repo"))
            }
        }
    }

    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
    }
}
