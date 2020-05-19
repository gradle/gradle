/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.Project
import org.gradle.build.ReleasedVersionsFromVersionControl
import org.gradle.kotlin.dsl.*
import org.gradle.gradlebuild.BuildEnvironment
import java.io.File
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

val Project.gitInfo
    get() = rootProject.extensions.getByName<GitInformationExtension>("gitInfo")


val Project.releasedVersions
    get() = ReleasedVersionsFromVersionControl(File(rootDir, "released-versions.json"), File(rootDir, "version.txt"))


open class GitInformationExtension(val project: Project) {
    val gradleBuildBranch: String
    val gradleBuildCommitId: String

    init {
        val repository by lazy { FileRepositoryBuilder().setGitDir(project.rootProject.projectDir.resolve(".git")).build() }
        gradleBuildBranch = System.getenv(BuildEnvironment.BUILD_BRANCH) ?: repository.branch
        gradleBuildCommitId = System.getenv(BuildEnvironment.BUILD_COMMIT_ID) ?: repository.resolve(repository.fullBranch).name
    }
}
