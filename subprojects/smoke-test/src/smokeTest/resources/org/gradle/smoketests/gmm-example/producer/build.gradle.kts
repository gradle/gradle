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
