/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.integtests.resolve.rules

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.HttpRepository

abstract class ComponentMetadataRulesStatusIntegrationTest extends AbstractHttpDependencyResolutionTest {
    abstract HttpRepository getRepo()

    abstract String getRepoDeclaration()

    def setup() {
        buildFile <<
"""
$repoDeclaration

configurations { compile }

dependencies {
    compile 'org.test:projectA:1.0'
}

task resolve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""
    }
}