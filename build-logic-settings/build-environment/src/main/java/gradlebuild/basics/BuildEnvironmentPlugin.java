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

package gradlebuild.basics;

import org.gradle.api.Plugin;
import org.gradle.api.file.Directory;
import org.gradle.api.initialization.Settings;
import org.gradle.api.provider.Provider;

public class BuildEnvironmentPlugin implements Plugin<Settings> {

    @Override
    public void apply(Settings settings) {
        Directory rootDirectory = settings.getLayout().getRootDirectory();
        settings.getGradle().getLifecycle().beforeProject(project -> {
            Provider<BuildEnvironmentService> service = project.getGradle().getSharedServices().registerIfAbsent(
                "buildEnvironmentService", BuildEnvironmentService.class, spec -> {
                    // We rely on the fact that root is configured first
                    if (!project.getPath().equals(":")) {
                        throw new IllegalStateException("BuildEnvironmentService should be registered by the root");
                    }
                    spec.getParameters().getRootProjectDir().set(rootDirectory);
                    spec.getParameters().getRootProjectBuildDir().set(project.getLayout().getBuildDirectory());
                });
            BuildEnvironmentExtension buildEnvironmentExtension = project.getExtensions().create("buildEnvironment", BuildEnvironmentExtension.class);
            buildEnvironmentExtension.getGitCommitId().set(service.flatMap(BuildEnvironmentService::getGitCommitId));
            buildEnvironmentExtension.getGitBranch().set(service.flatMap(BuildEnvironmentService::getGitBranch));
            buildEnvironmentExtension.getScriptTemplateCommitId().set(service.flatMap(BuildEnvironmentService::getScriptTemplateCommitId));
            buildEnvironmentExtension.getRepoRoot().set(rootDirectory);
            buildEnvironmentExtension.getRootProjectBuildDir().set(service.flatMap(s -> s.getParameters().getRootProjectBuildDir()));
        });
    }
}
