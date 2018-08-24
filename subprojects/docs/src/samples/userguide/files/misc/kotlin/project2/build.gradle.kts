// tag::using-root-dir-property[]
val configFile = file("${rootDir}/shared/config.xml")
// end::using-root-dir-property[]

tasks.create("checkConfigFile") {
    doLast {
        require(configFile.exists())
    }
}
