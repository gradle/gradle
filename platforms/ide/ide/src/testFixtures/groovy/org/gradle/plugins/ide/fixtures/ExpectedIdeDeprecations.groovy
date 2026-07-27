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

package org.gradle.plugins.ide.fixtures

trait ExpectedIdeDeprecations {
    void expectTaskDeprecations(String... taskNames) {
        for (String taskName : taskNames) {
            executer.expectDocumentedDeprecationWarning("The $taskName task has been deprecated. This is scheduled to be removed in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#ide_task_deprecation")
        }
    }

    void expectTaskTypeDeprecations(Map<String, Integer> types) {
        types.each { type, count ->
            (count ?: 1).times {
                executer.expectDocumentedDeprecationWarning("Using types related to file generation tasks of IDE plugins ($type). This behavior has been deprecated. This is scheduled to be removed in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#ide_task_deprecation")
            }
        }
    }
}
