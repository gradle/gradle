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

package org.gradle.integtests.tooling.r930

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.ProjectConnection

class KotlinDslPluginRelatedToolingApiSpecification extends ToolingApiSpecification {

    // Tooling API builds get no arguments from the executer. A system property in gradle.properties survives the arguments that the tests set.
    def setup() {
        def version = RepoScriptBlockUtil.locallyBuiltKotlinDslPluginsVersion()
        if (version != null && targetDist.gradleHomeDir == IntegrationTestBuildContext.INSTANCE.gradleHomeDir) {
            file("gradle.properties") << "systemProp.${RepoScriptBlockUtil.KOTLIN_DSL_PLUGINS_VERSION_OVERRIDE_PROPERTY}=${version}\n"
        }
    }

    @Override
    <T> T succeeds(@DelegatesTo(ProjectConnection) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.ProjectConnection"]) Closure<T> cl) {
        disableJdkWarningCheck()
        return super.succeeds(cl)
    }

    @Override
    void fails(@DelegatesTo(ProjectConnection) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.ProjectConnection"]) Closure cl) {
        disableJdkWarningCheck()
        super.fails(cl)
    }

    private boolean disableJdkWarningCheck() {
        // Doing this to avoid "The `embedded-kotlin` and `kotlin-dsl` plugins rely on features of Kotlin `X` that might work differently than in the compiler version `Y`." errors
        // on embedded Kotlin version bumps.
        withJdkWarningsCheckDisabled()
    }
}
