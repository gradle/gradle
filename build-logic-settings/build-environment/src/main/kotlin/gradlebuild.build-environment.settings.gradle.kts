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

import gradlebuild.basics.BuildEnvironmentExtension
import gradlebuild.basics.BuildEnvironmentService

with(layout.rootDirectory) {
    gradle.lifecycle.beforeProject {
        val service = gradle.sharedServices.registerIfAbsent("buildEnvironmentService", BuildEnvironmentService::class) {
            parameters.rootProjectDir = this@with
            // We rely on the fact that these properties are read for a root project, because root is configured first
            parameters.artifactoryUserName = providers.gradleProperty("artifactoryUserName")
            parameters.artifactoryPassword = providers.gradleProperty("artifactoryPassword")
        }
        val buildEnvironmentExtension = extensions.create("buildEnvironment", BuildEnvironmentExtension::class)
        buildEnvironmentExtension.gitCommitId = service.flatMap { it.gitCommitId }
        buildEnvironmentExtension.gitBranch = service.flatMap { it.gitBranch }
        buildEnvironmentExtension.artifactoryUserName = service.flatMap { it.parameters.artifactoryUserName }
        buildEnvironmentExtension.artifactoryPassword = service.flatMap { it.parameters.artifactoryPassword }
        buildEnvironmentExtension.repoRoot = this@with
    }
}
