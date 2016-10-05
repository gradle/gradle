/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.initialization

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.SetSystemProperties
import org.junit.Rule

class PropertiesLoaderIntegrationTest extends AbstractIntegrationSpec {
    @Rule SetSystemProperties systemProperties = new SetSystemProperties()

    def "build property set on command line takes precedence over properties file"() {
        when:
        file('gradle.properties') << """
org.gradle.configureondemand=true
"""
        buildFile << """
task assertCodEnabled {
    doLast {
        assert gradle.startParameter.configureOnDemand
    }
}

task assertCodDisabled {
    doLast {
        assert !gradle.startParameter.configureOnDemand
    }
}
"""

        then:
        succeeds ':assertCodEnabled'

        when:
        args '-Dorg.gradle.configureondemand=false'

        then:
        succeeds ':assertCodDisabled'
    }

    def "system property set on command line takes precedence over properties file"() {
        given:
        file('gradle.properties') << """
systemProp.mySystemProp=properties file
"""
        buildFile << """
task printSystemProp {
    doLast {
        println "mySystemProp=\${System.getProperty('mySystemProp')}"
    }
}
"""
        when:
        def result = run ':printSystemProp'

        then:
        result.assertOutputContains('mySystemProp=properties file')

        when:
        args '-DmySystemProp=commandline'
        result = run ':printSystemProp'

        then:
        result.assertOutputContains('mySystemProp=commandline')
    }

    def "build property set on command line takes precedence over jvm args"() {
        when:
        executer.requireGradleDistribution()
        executer.withEnvironmentVars 'GRADLE_OPTS': '-Dorg.gradle.configureondemand=true'

        buildFile << """
task assertCodEnabled {
    doLast {
        assert gradle.startParameter.configureOnDemand
    }
}

task assertCodDisabled {
    doLast {
        assert !gradle.startParameter.configureOnDemand
    }
}
"""

        then:
        succeeds ':assertCodEnabled'

        when:
        args '-Dorg.gradle.configureondemand=false'

        then:
        succeeds ':assertCodDisabled'
    }

    def "system property set on command line takes precedence over jvm args"() {
        given:
        executer.requireGradleDistribution()
        executer.withEnvironmentVars 'GRADLE_OPTS': '-DmySystemProp=jvmarg'

        buildFile << """
task printSystemProp {
    doLast {
        println "mySystemProp=\${System.getProperty('mySystemProp')}"
    }
}
"""
        when:
        def result = run ':printSystemProp'

        then:
        result.assertOutputContains('mySystemProp=jvmarg')

        when:
        args '-DmySystemProp=commandline'
        result = run ':printSystemProp'

        then:
        result.assertOutputContains('mySystemProp=commandline')
    }
}
