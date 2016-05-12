import org.gradle.script.lang.kotlin.*
import org.gradle.api.plugins.*

apply<ApplicationPlugin>()

configure<ApplicationPluginConvention> {
    mainClassName = "samples.HelloWorld"
}
