plugins {
    java
    application
    idea
}

group = "org.sample"
version = "1.0"

application {
    mainClassName = "org.sample.myapp.Main"
}

dependencies {
    implementation("org.sample:number-utils:1.0")
    implementation("org.sample:string-utils:1.0")
}

repositories {
    ivy {
        url = uri(project.file("../local-repo"))
    }
    jcenter()
}

// tag::publishDeps[]
tasks.register("publishDeps") {
    dependsOn(gradle.includedBuilds.map { it.task(":publishIvyPublicationToIvyRepository") })
}
// end::publishDeps[]
