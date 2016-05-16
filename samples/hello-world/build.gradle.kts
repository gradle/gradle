import org.gradle.api.plugins.*
import org.gradle.api.tasks.wrapper.*
import org.gradle.script.lang.kotlin.*

apply<ApplicationPlugin>()

configure<ApplicationPluginConvention> {
    mainClassName = "samples.HelloWorld"
}

tasks.withType<Wrapper> {
    distributionUrl = "https://repo.gradle.org/gradle/demo/demo.zip"
}
