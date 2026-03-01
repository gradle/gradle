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

package org.gradle.testing.junit.vintage

import groovy.transform.SelfType
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec

/**
 * Mixin that makes the test executor output verification ignore the otherwise un-expectable JUnit Vintage deprecation
 * warning in tests that run with JUnit 6's Vintage Engine.
 */
@SelfType(MultiVersionIntegrationSpec)
trait IgnoresJUnit6VintageDeprecationWarning {
    def setup() {
        getExecuter().ignoreLines(Collections.singletonList("(1) [INFO] The JUnit Vintage engine is deprecated and should only be used temporarily while migrating tests to JUnit Jupiter or another testing framework with native JUnit Platform support."))
    }
}
