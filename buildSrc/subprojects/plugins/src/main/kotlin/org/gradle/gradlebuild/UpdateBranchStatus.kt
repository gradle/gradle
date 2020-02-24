/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.gradlebuild

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.performance.determineCurrentBranch


/**
 * When a Ready for Nightly trigger build completes successfully on master/release, we publish a git tag
 * green-master/green-release to remote repository so that developers can checkout new branches from these tags.
 */
open class UpdateBranchStatus : DefaultTask() {
    @TaskAction
    fun publishBranchStatus() {
        when (project.determineCurrentBranch()) {
            "master" -> publishBranchStatus("master")
            "release" -> publishBranchStatus("release")
        }
    }

    private
    fun publishBranchStatus(branch: String) {
        project.execAndGetStdout("git", "push", "origin", "$branch:green-$branch")
    }
}
