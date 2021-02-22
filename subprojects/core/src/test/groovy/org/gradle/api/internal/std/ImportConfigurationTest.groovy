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

package org.gradle.api.internal.std

import spock.lang.Specification

class ImportConfigurationTest extends Specification {
    def "includes and excludes apply to their respective concern"() {
        def config = new ImportConfiguration(
            IncludeExcludePredicate.of(["lib1", "lib2"] as Set, null),
            IncludeExcludePredicate.of(["bundle1", "bundle2"] as Set, null),
            IncludeExcludePredicate.of(["v1", "v2"] as Set, null),
            IncludeExcludePredicate.of(["plugin1", "plugin2"] as Set, null),
        )

        expect:
        config.includeLibrary('lib1')
        !config.includeLibrary('lib3')

        and:
        config.includeBundle('bundle1')
        !config.includeBundle('bundle3')

        and:
        config.includeVersion('v1')
        !config.includeVersion('v3')


        and:
        config.includePlugin('plugin1')
        !config.includePlugin('plugin3')

    }
}
