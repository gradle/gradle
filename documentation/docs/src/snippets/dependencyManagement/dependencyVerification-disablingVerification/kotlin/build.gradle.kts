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
    isCanBeResolved = true
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
    inputs.files(myPluginClasspath)
    doLast {
        println(myPluginClasspath.files)
    }
}

// tag::disabling-detached-configuration[]
tasks.register("checkDetachedDependencies") {
    doLast {
        val detachedConf = configurations.detachedConfiguration(dependencies.create("org.apache.commons:commons-lang3:3.3.1"))
        detachedConf.resolutionStrategy.disableDependencyVerification()
        println(detachedConf.files)
    }
}
// end::disabling-detached-configuration[]
