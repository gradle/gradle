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
    id("gradlebuild.distribution.api-java")
}

/**
 * These classes would more naturally belong in the Build Init plugin project, but are separated out to avoid a circular dependency, as build-init
 * already depends upon Core, and Core contains the ProjectScopeServices class which needs to know about the template loading classes.
 */
description = "Contains implementations of template loading framework classes used by the Build Init plugin."

dependencies {
    api(projects.buildInitTemplatesApi)
    api(projects.loggingApi)

    implementation(projects.stdlibJavaExtensions)
}
