import org.gradle.api.tasks.*
import org.apache.tools.ant.filters.*

//for including in the copy task
val dataContent = copySpec {
    from("src/data")
    include("*.data")
}

task<Copy>("initConfig") {

    from("src/main/config") {
        include("**/*.properties")
        include("**/*.xml")
        filter<ReplaceTokens>(
            "tokens" to mapOf("version" to "2.3.1"))
    }

    from("src/main/languages") {
        rename("EN_US_(.*)", "$1")
    }

    into("build/target/config")
    exclude("**/*.bak")
    includeEmptyDirs = false
    with(dataContent)
}

task<Delete>("clean") {
    delete(buildDir)
}
