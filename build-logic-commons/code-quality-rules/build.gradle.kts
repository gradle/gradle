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

group = "gradlebuild"

dependencies {
    api(platform(projects.buildPlatform))
    compileOnly(localGroovy())
    compileOnly(buildLibs.codenarc) {
        // Groovy is provided by localGroovy() above, so none of CodeNarc's Groovy
        // distribution is wanted. The exclusion is group-wide on purpose: narrowing it
        // to today's modules would silently let a future CodeNarc Groovy artifact back in.
        exclude(group = "org.apache.groovy")
    }
}
