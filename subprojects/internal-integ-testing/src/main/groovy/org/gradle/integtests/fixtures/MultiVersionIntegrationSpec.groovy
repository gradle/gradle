/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.fixtures

import org.gradle.integtests.fixtures.compatibility.MultiVersionTest
import org.gradle.integtests.fixtures.compatibility.MultiVersionTestCategory
import org.gradle.util.internal.VersionNumber

import javax.annotation.Nullable

/**
 * See {@link org.gradle.integtests.fixtures.compatibility.AbstractContextualMultiVersionTestInterceptor} for information on running these tests.
 */
@MultiVersionTest
@MultiVersionTestCategory
abstract class MultiVersionIntegrationSpec extends AbstractIntegrationSpec {
    static final def CLASSIFIER_PATTERN = /^(.*?)(:.*)?$/

    static def version

    static VersionNumber getVersionNumber() {
        if (version == null) {
            throw new IllegalStateException("No version present")
        }
        def m = version.toString() =~ CLASSIFIER_PATTERN
        VersionNumber.parse(m[0][1])
    }

    @Nullable
    static String getVersionClassifier() {
        if (version == null) {
            throw new IllegalStateException("No version present")
        }
        def m = version.toString() =~ CLASSIFIER_PATTERN
        return m[0][2]
    }
}
