/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.tasks.testing.junitplatform

import org.gradle.util.TestUtil
import spock.lang.Specification

class JUnitPlatformOptionsTest extends Specification {

    def copyFromOverridesOldOptions() {
        given:
        def source = TestUtil.newInstance(JUnitPlatformOptions.class)
            .includeEngines("sourceIncludedCategory")
            .excludeEngines("sourceExcludedCategory")
            .includeTags("sourceIncludedTag")
            .excludeTags("sourceExcludedTag")

        when:
        def target = TestUtil.newInstance(JUnitPlatformOptions.class)
            .includeEngines("targetIncludedCategory")
            .excludeEngines("targetExcludedCategory")
            .includeTags("targetIncludedTag")
            .excludeTags("targetExcludedTag")
        target.copyFrom(source)

        then:
        with(target) {
            assert includeEngines.get() =~ ["sourceIncludedCategory"]
            assert excludeEngines.get() =~ ["sourceExcludedCategory"]
            assert includeTags.get() =~ ["sourceIncludedTag"]
            assert excludeTags.get() =~ ["sourceExcludedTag"]
        }
    }

}
