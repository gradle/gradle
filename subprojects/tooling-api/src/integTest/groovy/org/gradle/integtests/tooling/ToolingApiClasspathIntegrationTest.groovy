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

package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.tooling.fixture.ToolingApiDistribution
import org.gradle.integtests.tooling.fixture.ToolingApiDistributionResolver
import org.junit.Rule
import spock.lang.Specification
import spock.lang.IgnoreIf
import org.gradle.integtests.fixtures.GradleDistributionExecuter

@IgnoreIf({ !GradleDistributionExecuter.systemPropertyExecuter.forks })
class ToolingApiClasspathIntegrationTest extends Specification {
    @Rule public final GradleDistribution distribution = new GradleDistribution()

    def "tooling api classpath contains only tooling-api jar and slf4j"() {
        when:
        ToolingApiDistributionResolver resolver = new ToolingApiDistributionResolver().withDefaultRepository()
        ToolingApiDistribution resolve = resolver.resolve(distribution.getVersion())
        then:
        assert resolve != null
        assert resolve.classpath.files.size() == 2
        assert resolve.classpath.files.any {it.name ==~ /slf4j-api-.*\.jar/}
        assert resolve.classpath.files.find {it.name ==~ /gradle-tooling-api.*\.jar/}.size() < 1.5 * 1024 * 1024
    }
}
