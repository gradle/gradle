import org.gradle.api.plugins.*
import org.gradle.api.tasks.wrapper.*
import org.gradle.script.lang.kotlin.*

apply<ApplicationPlugin>()

configure<ApplicationPluginConvention> {
    mainClassName = "samples.HelloWorld"
}

repositories {
    jcenter()
}

dependencies {
    "testCompile"("junit:junit:4.12")
}

tasks.withType<Wrapper> {
    distributionUrl = "https://repo.gradle.org/gradle/demo/demo.zip"
}
