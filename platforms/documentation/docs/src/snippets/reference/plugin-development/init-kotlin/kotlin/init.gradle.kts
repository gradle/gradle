import com.example.BaselineInitPlugin

// This init script loads the baseline init plugin from mavenLocal and applies it to the build.
println("[INIT-SCRIPT] Loaded (Gradle ${gradle.gradleVersion})")

initscript {
    repositories {
        maven { url = uri("./baseline-init-plugin/build/repo") }
        mavenCentral()
        gradlePluginPortal()
    }
    // Either coordinates work; the ID form below relies on the classpath JAR containing the descriptor
    dependencies {
        classpath("com.example:baseline-init-plugin:0.1.0")
    }
}

// Apply the plugin
apply<BaselineInitPlugin>()
