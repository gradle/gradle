/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.fixtures.polyglot

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.test.fixtures.dsl.GradleDsl

@CompileStatic
class RepositoriesBuilder extends MultiSectionHandler implements SectionBuilder {
    @Override
    String getSectionName() {
        'repositories'
    }

    void mavenCentral() {
        sections << new SectionBuilder() {
            @Override
            String generateSection(GradleDsl dsl) {
                RepoScriptBlockUtil.mavenCentralRepositoryDefinition(dsl)
            }
        }
    }

    void maven(URI uri) {
        sections << new GenericSection({ """maven { url = "$uri" }""" }, { """maven { setUrl("$uri") }""" })
    }

    void ivy(URI uri) {
        sections << new GenericSection({ """ivy { url = "$uri" }""" }, { """ivy { setUrl("$uri") }""" })
    }

}
