import com.google.gson.Gson
import gradlebuild.basics.capitalize
import java.net.URI
import java.nio.charset.Charset

wrapperUpdateTask("nightly", "nightly")
wrapperUpdateTask("rc", "release-candidate")
wrapperUpdateTask("current", "current")

private
fun Provider<RegularFile>.writeText(text: String, charset: Charset = Charsets.UTF_8) = this.get().asFile.writeText(text, charset)

private
fun Provider<RegularFile>.readText(charset: Charset = Charsets.UTF_8) = this.get().asFile.readText(charset)

tasks.withType<Wrapper>().configureEach {
    val jvmOpts = "-Dfile.encoding=UTF-8"
    inputs.property("jvmOpts", jvmOpts)
    doLast {
        val optsEnvVar = "DEFAULT_JVM_OPTS"
        scriptFile.writeText(scriptFile.readText().replace("$optsEnvVar='", "$optsEnvVar='$jvmOpts "))
        batchScript.writeText(batchScript.readText().replace("set $optsEnvVar=", "set $optsEnvVar=$jvmOpts "))
    }
}

fun Project.wrapperUpdateTask(name: String, label: String) {
    val wrapperTaskName = "${name}Wrapper"
    val configureWrapperTaskName = "configure${wrapperTaskName.capitalize()}"

    val wrapperTask = tasks.register<Wrapper>(wrapperTaskName) {
        dependsOn(configureWrapperTaskName)
        group = "wrapper"
    }

    tasks.register(configureWrapperTaskName) {
        doLast {
            val jsonText = URI("https://services.gradle.org/versions/$label").toURL().readText()
            val versionInfo = Gson().fromJson(jsonText, VersionDownloadInfo::class.java)
            println("updating wrapper to $label version: ${versionInfo.version} (downloadUrl: ${versionInfo.downloadUrl})")
            wrapperTask.get().distributionUrl = versionInfo.downloadUrl
        }
    }
}

data class VersionDownloadInfo(val version: String, val downloadUrl: String)
