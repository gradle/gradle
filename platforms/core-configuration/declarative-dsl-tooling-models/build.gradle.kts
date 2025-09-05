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
    id("gradlebuild.distribution.implementation-kotlin")
    id("gradlebuild.publish-public-libraries")
}

description = "Declarative DSL Tooling Models for IDEs"

dependencies {
    compileOnly(projects.toolingApi)

    compileOnly(libs.kotlinStdlib) {
        because(
            "used by the compiler, but there should be no binary dependency on the stdlib; " +
                "this project should be usable by Kotlin-less consumers, see: `DeclarativeDslDependencyTest`"
        )
    }

    implementation("org.jspecify:jspecify:1.0.0")
}
