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
    val projectDir = layout.projectDirectory
    doLast {
        projectDir.file("ant").asFile.mkdirs()
        projectDir.file("libs").asFile.mkdirs()
        projectDir.file("tools").asFile.mkdirs()
        projectDir.file("ant/antcontrib.jar").asFile.createNewFile()
        projectDir.file("libs/commons-lang.jar").asFile.createNewFile()
        projectDir.file("libs/log4j.jar").asFile.createNewFile()
        projectDir.file("tools/a.exe").asFile.createNewFile()
        projectDir.file("tools/b.exe").asFile.createNewFile()
    }
}

tasks.register<Copy>("copyLibs") {
    dependsOn(tasks["createLibs"])
    from(configurations["antContrib"])
    from(configurations["externalLibs"])
    from(configurations["deploymentTools"])
    into(layout.buildDirectory.dir("libs"))
}
