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

open class FooExtension(project: Project) {
    val someDirectory: DirectoryProperty = project.layout.directoryProperty()
    val someFile: RegularFileProperty = project.layout.fileProperty()
    val someFiles: ConfigurableFileCollection = project.layout.configurableFiles()
}

project.extensions.create("foo", FooExtension::class, project)

configure<FooExtension> {
    someDirectory.set(project.layout.projectDirectory.dir("some-directory"))
    someFile.set(project.layout.buildDirectory.file("some-file"))
    someFiles.from(project.layout.configurableFiles(someDirectory, someFile))
}

task("print") {
    doLast {
        val foo = project.the<FooExtension>()
        val someDirectory = foo.someDirectory.get().asFile
        logger.quiet("foo.someDirectory = " + someDirectory)
        logger.quiet("foo.someFiles contains someDirectory? " + foo.someFiles.contains(someDirectory))

        val someFile = foo.someFile.get().asFile
        logger.quiet("foo.someFile = " + someFile)
        logger.quiet("foo.someFiles contains someFile? " + foo.someFiles.contains(someFile))
    }
}
