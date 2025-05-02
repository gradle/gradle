/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest

abstract class AbstractIvyDescriptorExcludeResolveIntegrationTest extends AbstractDependencyResolutionTest {
    protected static final EXCLUDE_ATTRIBUTE = 'exclude'

    def setup() {
        buildFile << """
repositories {
    ivy {
        url = "${ivyRepo.uri}"
    }
}

configurations {
    compile
}

dependencies {
    compile 'org.gradle.test:a:1.0'
}

task check(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""
    }

    protected void succeedsDependencyResolution() {
        succeeds 'check'
    }

    protected void assertResolvedFiles(List<String> files) {
        file('libs').assertHasDescendants(files as String[])
    }
}
