plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        register("instrumentedJarsPlugin") {
            id = "instrumented-jars"
            implementationClass = "com.acme.InstrumentedJarsPlugin"
        }
    }
}

