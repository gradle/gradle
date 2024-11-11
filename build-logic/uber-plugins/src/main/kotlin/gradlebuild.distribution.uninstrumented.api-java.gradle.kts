import gradle.kotlin.dsl.accessors._4d90ee38640727ba8d6a57a6ca35d262.apiElements
import gradle.kotlin.dsl.accessors._4d90ee38640727ba8d6a57a6ca35d262.compileOnlyApi

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
    id("gradlebuild.java-library")
    id("gradlebuild.distribution-module")
    id("gradlebuild.distribution.api")
}

description = "Used for Gradle API projects that don't apply instrumentation"



configurations {
    // TODO: Why are we not generating extensions for this configuration?
    named("apiStubElements") {
        extendsFrom(configurations.compileOnlyApi.get())

        // For now, use the regular artifacts so things don't break.
        // TODO: Use ABI filtered artifacts instead
        artifacts.addAllLater(configurations.apiElements.map { it.artifacts })

        // TODO: WRITE A TASK TO GET THE ABI CLASSES
        // outgoing.artifact(abiClassesDirectory)
    }
}
