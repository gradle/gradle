/*
 * Copyright 2018 the original author or authors.
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

plugins {
    base
}

// Fake NPM task that would normally execute npm with its provided arguments
open class NpmTask : DefaultTask() {

    open val args = project.objects.listProperty<String>()

    @TaskAction
    fun run() {
        project.file("${project.buildDir}/bundle.js").outputStream().use { stream ->
            project.file("scripts").listFiles().sorted().forEach {
                stream.write(it.readBytes())
            }
        }
    }
}

// tag::bundle-task[]
@CacheableTask
open class BundleTask : NpmTask() {
    
    @get:Internal
    override val args
        get() = super.args

    
    @get:InputDirectory
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val scripts: DirectoryProperty = project.objects.directoryProperty()

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val configFiles: ConfigurableFileCollection = project.files()

    @get:OutputFile
    val bundle: RegularFileProperty = project.objects.fileProperty()

    init {
        args.addAll("run", "bundle")
        bundle.set(project.layout.buildDirectory.file("bundle.js"))
        scripts.set(project.layout.projectDirectory.dir("scripts"))
        configFiles.from(project.layout.projectDirectory.file("package.json"))
        configFiles.from(project.layout.projectDirectory.file("package-lock.json"))
    }
}

val bundle by tasks.creating(BundleTask::class)
// end::bundle-task[]

task("printBundle") {
    dependsOn(bundle)
    doLast {
        println(file("$buildDir/bundle.js").readText())
    }
}
