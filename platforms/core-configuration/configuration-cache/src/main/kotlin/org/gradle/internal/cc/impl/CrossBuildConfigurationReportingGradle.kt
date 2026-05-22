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

package org.gradle.internal.cc.impl

import org.gradle.api.internal.GradleInternal
import org.gradle.internal.configuration.problems.IsolatedProjectsProblemsReporter
import org.gradle.internal.extensions.stdlib.capitalized

internal
class CrossBuildConfigurationReportingGradle(
    gradle: GradleInternal,
    private val referrer: GradleInternal,
    private val ipProblems: IsolatedProjectsProblemsReporter
) : MutableStateAccessAwareGradle(gradle) {

    override fun onMutableStateAccess(what: String) {
        ipProblems.report {
            problem {
                text("Build ")
                reference(referrer.identityPath.asString())
                text(" cannot access Gradle.$what on build ")
                reference(identityPath.asString())
            }
                .exception { message -> message.capitalized() }
                .build()
        }
    }

    override fun getParent(): GradleInternal? =
        delegate.parent?.let { delegateParent ->
            CrossBuildConfigurationReportingGradle(delegateParent, referrer, ipProblems)
        }

    override fun getRoot(): GradleInternal =
        when (val root = delegate.root) {
            delegate -> this
            else -> CrossBuildConfigurationReportingGradle(root, referrer, ipProblems)
        }

    // Only report for supported listeners; other listener types are already reported as CC problems
    override fun addListener(listener: Any) {
        if (isSupportedListener(listener)) {
            onMutableStateAccess("addListener")
        }
        delegate.addListener(listener)
    }
}
