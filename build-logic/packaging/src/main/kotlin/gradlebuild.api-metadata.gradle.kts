/*
 * Copyright 2020 the original author or authors.
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

import gradlebuild.basics.PublicApi

/**
 * Generates Gradle API metadata resources.
 *
 * Include and exclude patterns for the Gradle API.
 * Parameter names for the Gradle API.
 */
plugins {
    java
}

val apiDeclarationPropertiesFile = generatedPropertiesFileFor("gradle-api-declaration")

val apiDeclaration by tasks.registering(WriteProperties::class) {
    property("includes", PublicApi.includes.joinToString(":"))
    property("excludes", PublicApi.excludes.joinToString(":"))
    destinationFile = apiDeclarationPropertiesFile
}

sourceSets.main {
    output.dir(mapOf("builtBy" to apiDeclaration), apiDeclarationPropertiesFile.map { it.asFile.parentFile })
}

fun generatedPropertiesFileFor(name: String) =
    layout.buildDirectory.file("generated-api-metadata/$name/$name.properties")
