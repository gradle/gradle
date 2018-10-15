// tag::cross[]
import com.github.jengelman.gradle.plugins.shadow.ShadowExtension
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import ratpack.gradle.RatpackExtension

// tag::root[]
plugins {
    id("com.github.johnrengelman.shadow") version "4.0.1" apply false
    id("io.ratpack.ratpack-java") version "1.5.4" apply false
}
// end::root[]

// end::cross[]
subprojects {
    apply(plugin = "java-base")
    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

// tag::cross[]
project(":domain") {
    apply(plugin = "java-library")
    dependencies {
        "api"("javax.measure:unit-api:1.0")
        "implementation"("tec.units:unit-ri:1.0.3")
    }
}

project(":infra") {
    apply(plugin = "java-library")
    apply(plugin = "com.github.johnrengelman.shadow")
    configure<ShadowExtension> {
        applicationDistribution.from("src/dist")
    }
    tasks.named<ShadowJar>("shadowJar") {
        minimize()
    }
}

project(":http") {
    apply(plugin = "java")
    apply(plugin = "io.ratpack.ratpack-java")
    val ratpack = the<RatpackExtension>()
    dependencies {
        "implementation"(project(":domain"))
        "implementation"(project(":infra"))
        "implementation"(ratpack.dependency("dropwizard-metrics"))
        "runtime"("org.slf4j:slf4j-simple:1.7.25")
    }
    configure<ApplicationPluginConvention> {
        mainClassName = "example.App"
    }
    ratpack.baseDir = file("src/ratpack/baseDir")
}
// end::cross[]
