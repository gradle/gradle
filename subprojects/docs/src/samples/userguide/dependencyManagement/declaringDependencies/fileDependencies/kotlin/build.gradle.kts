// tag::file-dependencies[]
configurations {
    create("antContrib")
    create("externalLibs")
    create("deploymentTools")
}

dependencies {
    "antContrib"(files("ant/antcontrib.jar"))
    "externalLibs"(files("libs/commons-lang.jar", "libs/log4j.jar"))
    "deploymentTools"(fileTree("tools") { include("*.exe") })
}
// end::file-dependencies[]

tasks.register("createLibs") {
    doLast {
        file("ant").mkdirs()
        file("libs").mkdirs()
        file("tools").mkdirs()
        file("ant/antcontrib.jar").createNewFile()
        file("libs/commons-lang.jar").createNewFile()
        file("libs/log4j.jar").createNewFile()
        file("tools/a.exe").createNewFile()
        file("tools/b.exe").createNewFile()
    }
}

tasks.register<Copy>("copyLibs") {
    dependsOn(tasks["createLibs"])
    from(configurations["antContrib"])
    from(configurations["externalLibs"])
    from(configurations["deploymentTools"])
    into("$buildDir/libs")
}
