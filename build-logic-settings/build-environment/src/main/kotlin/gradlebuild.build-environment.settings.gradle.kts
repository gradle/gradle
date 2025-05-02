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
            check(project.path == ":") {
                // We rely on the fact that root is configured first
                "BuildEnvironmentService should be registered by the root"
            }
            parameters.rootProjectDir = this@with
            parameters.rootProjectBuildDir = project.layout.buildDirectory
        }
        val buildEnvironmentExtension = extensions.create("buildEnvironment", BuildEnvironmentExtension::class)
        buildEnvironmentExtension.gitCommitId = service.flatMap { it.gitCommitId }
        buildEnvironmentExtension.gitBranch = service.flatMap { it.gitBranch }
        buildEnvironmentExtension.repoRoot = this@with
        buildEnvironmentExtension.rootProjectBuildDir = service.flatMap { it.parameters.rootProjectBuildDir }
    }
}
