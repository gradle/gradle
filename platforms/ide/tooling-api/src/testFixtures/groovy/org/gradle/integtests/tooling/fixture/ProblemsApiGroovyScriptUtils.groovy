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

    /**
     * The first version with the predefined problem group hierarchy ({@code Problems.getGroups()}). From this version on the
     * scripts create ids below {@code Others > Generic}, named after the former display name, because the legacy
     * {@code ProblemGroup.create()} identity is deprecated. A predefined id has no separate display name, so the former name is dropped.
     */
    static final GradleVersion PREDEFINED_GROUPS_VERSION = GradleVersion.version("9.9")

    static boolean hasPredefinedGroups(GradleVersion targetVersion) {
        targetVersion >= PREDEFINED_GROUPS_VERSION
    }

    /**
     * @param problems the expression the script reaches the Problems service with, for example {@code getProblems()} in a task or {@code problemsService} in a model builder
     */
    static String report(GradleVersion targetVersion, String idName = 'id', String idDisplayName = 'shortProblemMessage', String problems = 'getProblems()') {
        if (targetVersion < GradleVersion.version("8.6")) {
            'create'
        } else if (targetVersion < GradleVersion.version("8.11")) {
            'forNamespace("org.example.plugin").reporting '
        } else if (targetVersion < GradleVersion.version("8.13")) {
            'getReporter().reporting '
        } else {
            "getReporter().report(${createIdExpression(targetVersion, idName, idDisplayName, problems)}) "
        }
    }

    static String id(GradleVersion targetVersion, String name = 'type', String displayName = 'label', String problems = 'getProblems()') {
        if (targetVersion < GradleVersion.version("8.8")) {
            "label(\"$displayName\").category(\"$name\")"
        } else if (targetVersion < GradleVersion.version("8.13")) {
            "id(\"$name\", \"$displayName\")"
        } else {
            "id(${createIdExpression(targetVersion, name, displayName, problems)})"
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

    /**
     * An expression creating the problem id; {@code problems} is how the script reaches the Problems service.
     */
    static String createIdExpression(GradleVersion targetVersion, String name = 'type', String displayName = 'label', String problems = 'getProblems()') {
        if (hasPredefinedGroups(targetVersion)) {
            "${problems}.getGroups().getOthers().group(\"Generic\").problemId(\"$displayName\")"
        } else {
            "org.gradle.api.problems.ProblemId.create(\"$name\", \"$displayName\", org.gradle.api.problems.ProblemGroup.create(\"generic\", \"Generic\"))"
        }
    }
}
