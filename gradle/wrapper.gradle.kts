import java.net.URL

/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

fun wrapperUpdateTask(name: String, label: String, tasks: TaskContainer) {
    val wrapperTaskName = "${name}Wrapper"
    val configureWrapperTaskName = "configure${wrapperTaskName.capitalize()}"

    tasks.create(configureWrapperTaskName) {
        doLast {
            val wrapperTask = tasks.get(wrapperTaskName) as Wrapper
            val versionObject = groovy.json.JsonSlurper().parseText(URL("https://services.gradle.org/versions/$label").readText())
            if (versionObject == null) {
                throw GradleException("Cannot update wrapper to '${label}' version as there is currently no version of that label")
            }
            val (version, downloadUrl) = versionObject.withGroovyBuilder {
                getProperty("version") as String to getProperty("downloadUrl") as String
            }
            println("updating wrapper to $label version: ${version} (downloadUrl: ${downloadUrl})")
            wrapperTask.distributionUrl = downloadUrl
        }
    }

    tasks.create<Wrapper>(wrapperTaskName) {
        dependsOn(configureWrapperTaskName)
        group = "wrapper"
    }
}

tasks.withType<Wrapper>() {
    val jvmOpts = "-Xmx128m -Dfile.encoding=UTF-8"
    inputs.property("jvmOpts", jvmOpts)
    doLast {
        val optsEnvVar = "DEFAULT_JVM_OPTS"
        scriptFile.writeText(scriptFile.readText().replace("$optsEnvVar=\"\"", "$optsEnvVar=\"$jvmOpts\""))
        batchScript.writeText(batchScript.readText().replace("set $optsEnvVar=", "set $optsEnvVar=$jvmOpts"))
    }
}
wrapperUpdateTask("nightly", "nightly", tasks)
wrapperUpdateTask("rc", "release-candidate", tasks)
wrapperUpdateTask("current", "current", tasks)
