import groovy.lang.GroovySystem
import org.gradle.util.internal.VersionNumber

/*
 * Copyright 2022 the original author or authors.
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
    id("java-library")
}
description = "Provides a custom CodeNarc rule used by the Gradle build"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

group = "gradlebuild"

val groovyVersion = GroovySystem.getVersion()
val isGroovy4 = VersionNumber.parse(groovyVersion).major >= 4
val codenarcVersion = if (isGroovy4) "3.1.0-groovy-4.0" else "3.1.0"

dependencies {
    compileOnly(localGroovy())
    compileOnly("org.codenarc:CodeNarc:$codenarcVersion") {
        exclude(group = "org.apache.groovy")
        exclude(group = "org.codehaus.groovy")
    }
}
