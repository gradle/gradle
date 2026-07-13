/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.plugins.quality.Checkstyle

plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Prototype native gRPC tooling API wire contract (generated stubs). Target beta."

// This module is entirely protoc/grpc-generated Java that does not follow Gradle's strict
// no-warning policy. Disable Error Prone, -Werror and Checkstyle for the generated sources.
sourceSets {
    main {
        errorprone.enabled = false
    }
}
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.remove("-Werror")
}
tasks.withType<Checkstyle>().configureEach {
    enabled = false
}

dependencies {
    api(libs.grpcApi)
    api(libs.grpcStub)
    api(libs.protobufJava)
    api(libs.guava)

    implementation(libs.grpcProtobuf)

    compileOnly(libs.jspecify)
}

gradleModule {
    computedRuntimes {
        // gRPC server runs in the daemon; classified for both runtimes so the launcher
        // (client + daemon) can depend on it without a target-runtime mismatch.
        client = true
        daemon = true
    }
}
