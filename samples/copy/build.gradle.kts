import org.gradle.api.tasks.*
import org.gradle.api.tasks.wrapper.*
import org.gradle.script.lang.kotlin.*
import org.apache.tools.ant.filters.*

//for including in the copy task
val dataContent = copySpec {
    it.from("src/data")
    it.include("*.data")
}

task<Copy>("initConfig") {

    from("src/main/config") {
        it.include("**/*.properties")
        it.include("**/*.xml")
        it.filter<ReplaceTokens>(
            "tokens" to mapOf("version" to "2.3.1"))
    }

    from("src/main/languages") {
        it.rename("EN_US_(.*)", "$1")
    }

    into("build/target/config")
    exclude("**/*.bak")
    includeEmptyDirs = false
    with(dataContent)
}

task<Delete>("clean") {
    delete(buildDir)
}

tasks.withType<Wrapper> {
    distributionUrl = "https://repo.gradle.org/gradle/demo/demo-3.zip"
}
