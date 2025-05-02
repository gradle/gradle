/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.performance.fixture

import org.gradle.integtests.fixtures.executer.DefaultGradleDistribution
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.performance.results.ResultsStoreHelper
import org.gradle.util.GradleVersion
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

import static BaselineVersionResolver.toBaselineVersions

class BaselineVersionResolverTest extends Specification {
    @Rule
    SetSystemProperties properties = new SetSystemProperties([(ResultsStoreHelper.SYSPROP_PERFORMANCE_TEST_CHANNEL): 'historical-master'])
    private ReleasedVersionDistributions distributions = Mock()

    def setup() {
        _ * distributions.mostRecentRelease >> dist('6.1')
        _ * distributions.all >> ['2.14.1', '3.5.1', '4.10.2', '6.1'].collect { dist(it) }
    }

    private static GradleDistribution dist(String version) {
        new DefaultGradleDistribution(GradleVersion.version(version), null, null)
    }

    def 'nightly can be used if minimumBaseVersion matched'() {
        expect:
        toBaselineVersions(distributions, ['6.0-20190823180744+0000'], '6.0') == ['6.0-20190823180744+0000'] as LinkedHashSet
    }

    def 'throw exception if all versions are filtered out by minimumBaseVersion'() {
        setup:
        System.setProperty(ResultsStoreHelper.SYSPROP_PERFORMANCE_TEST_CHANNEL, '')

        when:
        toBaselineVersions(distributions, ['6.0-20190823180744+0000'], '6.1')

        then:
        def e = thrown(AssertionError)
        e.message.contains('No versions selected: [6.0-20190823180744+0000]')
    }

    def 'latest release is added if no versions specified'() {
        expect:
        toBaselineVersions(distributions, [], null) == ['6.1'] as LinkedHashSet
    }
}
