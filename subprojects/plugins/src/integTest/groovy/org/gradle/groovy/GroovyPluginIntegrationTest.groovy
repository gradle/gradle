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
package org.gradle.groovy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class GroovyPluginIntegrationTest extends AbstractIntegrationSpec {

    def groovyConfigurationCanStillBeUsedButIsDeprecated() {
        given:
        buildFile << """
            apply plugin: "groovy"

            repositories {
                mavenCentral()
            }

            dependencies {
                groovy "org.codehaus.groovy:groovy-all:2.0.5"
                compile "com.google.guava:guava:11.0.2"
            }
        """

        file("src/main/groovy/Thing.groovy") << """
            import com.google.common.base.Strings

            class Thing {}
        """

        and:
        executer.withDeprecationChecksDisabled()

        when:
        succeeds("build")

        then:
        output.contains("The groovy configuration has been deprecated")
    }
}
