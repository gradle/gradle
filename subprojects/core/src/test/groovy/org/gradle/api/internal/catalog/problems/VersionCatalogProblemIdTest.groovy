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

package org.gradle.api.internal.catalog.problems

import org.gradle.util.TestUtil
import spock.lang.Specification

class VersionCatalogProblemIdTest extends Specification {

    def groups = TestUtil.problemsService().groups

    def "#id is reported in Dependencies > Declaration under its name"() {
        when:
        def problemId = id.problemId(groups)

        then:
        problemId.group == groups.dependencies.declaration
        problemId.name == id.name
        problemId.displayName == id.name

        where:
        id << VersionCatalogProblemId.values()
    }
}
