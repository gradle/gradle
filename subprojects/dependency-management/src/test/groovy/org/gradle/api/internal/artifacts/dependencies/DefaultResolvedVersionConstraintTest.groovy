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

package org.gradle.api.internal.artifacts.dependencies

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.InverseVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import spock.lang.Specification

class DefaultResolvedVersionConstraintTest extends Specification {
    private final VersionParser versionParser = new VersionParser()
    private final DefaultVersionSelectorScheme versionSelectorScheme = new DefaultVersionSelectorScheme(new DefaultVersionComparator(), versionParser)

    def "computes the complement of strict version #strictVersion"() {
        when:
        def e = new DefaultResolvedVersionConstraint('', '', strictVersion, [], versionSelectorScheme)

        then:
        e.preferredSelector == null
        e.requiredSelector.selector == strictVersion
        e.rejectedSelector instanceof InverseVersionSelector
        e.rejectedSelector.selector == complement
        !e.rejectAll

        where:
        strictVersion    | complement
        '1.0'            | '!(1.0)'
        '[1.0, 2.0]'     | '!([1.0, 2.0])'
        '[1.0, 2.0)'     | '!([1.0, 2.0))'
        '(, 2.0)'        | '!((, 2.0))'
        '(, 2.0]'        | '!((, 2.0])'
        '[1.0,)'         | '!([1.0,))'
        '1.+'            | '!(1.+)'
        '1+'             | '!(1+)'
        'latest.release' | '!(latest.release)'
    }

}
