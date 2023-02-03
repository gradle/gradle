// tag::using-root-dir-property[]
val configFile = file("$rootDir/shared/config.xml")
// end::using-root-dir-property[]

tasks.register("checkConfigFile") {
    val configFile = configFile  // Reduce scope of property for compatibility with the configuration cache

    doLast {
        require(configFile.exists())
    }
}
