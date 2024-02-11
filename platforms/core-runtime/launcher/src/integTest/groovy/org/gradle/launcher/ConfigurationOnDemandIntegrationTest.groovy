/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.launcher

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ProjectLifecycleFixture
import org.junit.Rule

class ConfigurationOnDemandIntegrationTest extends AbstractIntegrationSpec {

    @Rule ProjectLifecycleFixture fixture = new ProjectLifecycleFixture(executer, temporaryFolder)

    def "start parameter informs about the configuration on demand mode"() {
        file("gradle.properties") << "org.gradle.configureondemand=true"
        buildFile << "assert gradle.startParameter.configureOnDemand"
        expect:
        run()
    }

    def "can be enabled from command line and start parameter informs about it, too"() {
        file("gradle.properties") << "org.gradle.configureondemand=false"

        createDirs("api", "impl")
        settingsFile << "include 'api', 'impl'"
        buildFile << """
            allprojects { task foo };
            assert gradle.startParameter.configureOnDemand
        """

        when:
        run("--configure-on-demand", ":api:foo")

        then:
        fixture.assertProjectsConfigured(":", ":api")
    }
}
