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

import spock.lang.Specification

class JUnitPlatformOptionsTest extends Specification {

    def copyFromOverridesOldOptions() {
        given:
        def source = new JUnitPlatformOptions()
            .includeEngines("sourceIncludedCategory")
            .excludeEngines("sourceExcludedCategory")
            .includeTags("sourceIncludedTag")
            .excludeTags("sourceExcludedTag")

        when:
        def target = new JUnitPlatformOptions()
            .includeEngines("targetIncludedCategory")
            .excludeEngines("targetExcludedCategory")
            .includeTags("targetIncludedTag")
            .excludeTags("targetExcludedTag")
        target.copyFrom(source)

        then:
        with(target) {
            includeEngines =~ ["sourceIncludedCategory"]
            excludeEngines =~ ["sourceExcludedCategory"]
            includeTags =~ ["sourceIncludedTag"]
            excludeTags =~ ["sourceExcludedTag"]
        }
    }

}
