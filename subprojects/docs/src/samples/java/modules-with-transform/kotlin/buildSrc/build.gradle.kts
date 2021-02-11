plugins {
    `java-gradle-plugin` // so we can assign and ID to our plugin
}

dependencies {
    implementation("org.ow2.asm:asm:8.0.1")
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        // here we register our plugin with an ID
        register("extra-java-module-info") {
            id = "extra-java-module-info"
            implementationClass = "org.gradle.sample.transform.javamodules.ExtraModuleInfoPlugin"
        }
    }
}
