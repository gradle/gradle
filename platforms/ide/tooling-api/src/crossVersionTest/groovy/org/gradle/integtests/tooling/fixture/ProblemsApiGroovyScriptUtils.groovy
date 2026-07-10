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

package org.gradle.integtests.tooling.fixture

import org.gradle.util.GradleVersion

class ProblemsApiGroovyScriptUtils {

    static String report(GradleVersion targetVersion, String idName = 'id', String idDisplayName = 'shortProblemMessage') {
        if (targetVersion < GradleVersion.version("8.6")) {
            'create'
        } else if (targetVersion < GradleVersion.version("8.11")) {
            'forNamespace("org.example.plugin").reporting '
        } else if (targetVersion < GradleVersion.version("8.13")) {
            'getReporter().reporting '
        } else {
            "getReporter().report(org.gradle.api.problems.ProblemId.create(\"$idName\", \"$idDisplayName\", org.gradle.api.problems.ProblemGroup.create(\"generic\", \"Generic\"))) "
        }
    }

    static String id(GradleVersion targetVersion, String name = 'type', String displayName = 'label') {
        if (targetVersion < GradleVersion.version("8.8")) {
            "label(\"$displayName\").category(\"$name\")"
        } else if (targetVersion < GradleVersion.version("8.13")) {
            "id(\"$name\", \"$displayName\")"
        } else {
            "id(${createIdExpression(name, displayName)})"
        }
    }

    static String additionalData(GradleVersion targetVersion, String key = 'keyToString', String value = 'value') {
        if (targetVersion < GradleVersion.version("8.9")) {
            ".additionalData(\"$key\", \"$value\")\""
        } else if (targetVersion < GradleVersion.version("8.13")) {
            ".additionalData(org.gradle.api.problems.internal.GeneralDataSpec) { it.put(\"$key\", \"$value\") }"
        } else {
            ".additionalDataInternal(org.gradle.api.problems.internal.GeneralDataSpec) { it.put(\"$key\", \"$value\") }"
        }
    }

    static String createIdExpression(String name = 'type', String displayName = 'label') {
        "org.gradle.api.problems.ProblemId.create(\"$name\", \"$displayName\", org.gradle.api.problems.ProblemGroup.create(\"generic\", \"Generic\"))"
    }
}
