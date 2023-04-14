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

package org.gradle.configurationcache

import org.gradle.api.internal.GradleInternal
import org.gradle.execution.plan.Node
import org.gradle.internal.build.BuildState


interface VintageGradleBuild {
    val isRootBuild: Boolean
    val state: BuildState
    val gradle: GradleInternal
    val hasScheduledWork: Boolean
    val scheduledWork: List<Node>
}
