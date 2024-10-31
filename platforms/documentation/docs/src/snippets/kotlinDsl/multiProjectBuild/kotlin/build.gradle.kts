// tag::cross[]
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import ratpack.gradle.RatpackExtension

// tag::root[]
plugins {
    id("com.gradleup.shadow") version "8.3.4" apply false
    id("io.ratpack.ratpack-java") version "1.8.2" apply false
}
// end::root[]

project(":domain") {
    apply(plugin = "java-library")
    repositories { mavenCentral() }
    dependencies {
        "api"("javax.measure:unit-api:1.0")
        "implementation"("tec.units:unit-ri:1.0.3")
    }
}

project(":infra") {
    apply(plugin = "java-library")
    apply(plugin = "com.gradleup.shadow")
    tasks.named<ShadowJar>("shadowJar") {
        minimize()
    }
}

project(":http") {
    apply(plugin = "java")
    apply(plugin = "io.ratpack.ratpack-java")
    repositories { mavenCentral() }
    val ratpack = the<RatpackExtension>()
    dependencies {
        "implementation"(project(":domain"))
        "implementation"(project(":infra"))
        "implementation"(ratpack.dependency("dropwizard-metrics"))
        "runtimeOnly"("org.slf4j:slf4j-simple:1.7.25")
    }
    configure<JavaApplication> {
        mainClass = "example.App"
    }
    ratpack.baseDir = file("src/ratpack/baseDir")
}
// end::cross[]
