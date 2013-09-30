/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.language.c.plugins

import org.gradle.language.c.CSourceSet
import org.gradle.util.TestUtil
import spock.lang.Specification

class CLangPluginTest extends Specification {
    final def project = TestUtil.createRootProject()

    def "adds support for custom CSourceSets"() {
        when:
        project.plugins.apply(CLangPlugin)
        project.sources.create "test"

        then:
        project.sources.test.create("test_c", CSourceSet) in CSourceSet
    }

    def "adds conventional CSourceSet"() {
        when:
        project.plugins.apply(CLangPlugin)
        project.sources.create "test"

        then:
        project.sources.test.c in CSourceSet
    }
}
