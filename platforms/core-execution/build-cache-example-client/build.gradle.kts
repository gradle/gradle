/*
 * Copyright 2024 the original author or authors.
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
    id("gradlebuild.internal.java")
    id("application")
}

description = "Example client application using the build-cache library"

errorprone {
    disabledChecks.addAll(
        "InlineMeSuggester",
        "CloseableProvides",
    )
}

dependencies {
    api(projects.baseAnnotations)
    api(projects.buildCache)
    api(projects.buildCacheLocal)
    api(projects.snapshots)

    api(libs.guava)
    api(libs.guice)
    api(libs.slf4jApi)
    api(libs.commonsIo)
    // TODO Add this to the global version catalog
//    api("org.slf4j:slf4j-simple:1.7.30")
}

application {
    mainClass = "org.gradle.caching.example.ExampleBuildCacheClient"
}
