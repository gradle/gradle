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

package gradlebuild

import gradlebuildJava
import accessors.base
import accessors.sourceSets
import build.ParameterNamesIndex
import org.gradle.gradlebuild.PublicApi


val main by sourceSets

val parameterNamesIndex by tasks.registering(ParameterNamesIndex::class) {
    sources.from(main.allJava.matching {
        include(PublicApi.includes)
        exclude(PublicApi.excludes)
    })
    classpath.from(main.compileClasspath)
    classpath.from(tasks.named<JavaCompile>("compileJava"))
    if (file("src/main/groovy").isDirectory) {
        classpath.from(tasks.named<GroovyCompile>("compileGroovy"))
    }
    destinationFile.set(
        gradlebuildJava.generatedResourcesDir.file("${base.archivesBaseName}-parameter-names.properties")
    )
}

main.output.dir(
    gradlebuildJava.generatedResourcesDir,
    "builtBy" to parameterNamesIndex
)
