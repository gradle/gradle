/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.plugins.antlr

import org.gradle.integtests.fixtures.executer.GradleExecuter

trait AntlrDeprecationFixture {
    static GradleExecuter expectPackageArgumentDeprecationWarning(GradleExecuter executer) {
        return executer.expectDocumentedDeprecationWarning(
            "Setting the '-package' argument directly on AntlrTask has been deprecated." +
                " This will fail with an error in Gradle 10." +
                " Use the 'packageName' property of the AntlrTask to specify the package name instead of using the '-package' argument." +
                " For more information, please refer to https://docs.gradle.org/current/dsl/org.gradle.api.plugins.antlr.AntlrTask.html#org.gradle.api.plugins.antlr.AntlrTask:packageName in the Gradle documentation."
        )
    }
}
