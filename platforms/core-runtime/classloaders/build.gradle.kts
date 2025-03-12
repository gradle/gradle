/*
 * Copyright 2025 the original author or authors.
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
    id("gradlebuild.distribution.implementation-java")
    // TODO Make :serialization not depend on this and then we can unpublish this library
    id("gradlebuild.publish-public-libraries")
}

description = "Tools to handle classloaders"

gradlebuildJava{
    usedInWorkers()
    usesFutureStdlib = true
}

dependencies {
    api(projects.hashing)
    api(projects.stdlibJavaExtensions)

    api(libs.jspecify)

    implementation(projects.concurrent)
    implementation(projects.io)

    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.guava)

    compileOnly(libs.errorProneAnnotations)
}
