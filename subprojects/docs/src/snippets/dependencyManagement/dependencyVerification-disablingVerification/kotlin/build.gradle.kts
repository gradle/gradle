repositories {
    mavenCentral()
}

val myPlugin by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = false
}
val myPluginClasspath by configurations.creating {
    extendsFrom(myPlugin)
    isCanBeConsumed = false
}

dependencies {
    myPlugin("org.apache.commons:commons-lang3:3.3.1")
}

// tag::disabling-one-configuration[]
configurations {
    "myPluginClasspath" {
        resolutionStrategy {
            disableDependencyVerification()
        }
    }
}
// end::disabling-one-configuration[]

tasks.register("checkDependencies") {
    val classpath: FileCollection = myPluginClasspath
    inputs.files(classpath)
    doLast {
        println(classpath.files)
    }
}

// tag::disabling-detached-configuration[]
tasks.register("checkDetachedDependencies") {
    val detachedConf: FileCollection = configurations.detachedConfiguration(dependencies.create("org.apache.commons:commons-lang3:3.3.1")).apply {
        resolutionStrategy.disableDependencyVerification()
    }
    doLast {
        println(detachedConf.files)
    }
}
// end::disabling-detached-configuration[]
