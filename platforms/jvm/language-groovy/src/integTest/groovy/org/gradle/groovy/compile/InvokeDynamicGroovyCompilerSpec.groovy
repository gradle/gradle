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

package org.gradle.groovy.compile

import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.fixture.GroovyCoverage
import org.gradle.util.internal.VersionNumber

import static org.gradle.util.internal.GroovyDependencyUtil.groovyModuleDependency

@TargetCoverage({
    GroovyCoverage.SUPPORTS_INDY.collect {
        VersionNumber.parse(it).major >= 4
            ? it
            : "$it:indy"
    }
})
class InvokeDynamicGroovyCompilerSpec extends AbstractApiGroovyCompilerIntegrationSpec {
    def canEnableAndDisableInvokeDynamicOptimization() {
        // Disabling indy needs the optional groovy-callsite module since Groovy 6
        if (versionNumber.major >= 6) {
            buildFile << """
                dependencies {
                    implementation '${groovyModuleDependency("groovy-callsite", versionNumber)}'
                }
            """
        }

        when:
        run("sanityCheck")

        then:
        noExceptionThrown()
    }

    String compilerConfiguration() {
        '''
tasks.withType(GroovyCompile) {
    groovyOptions.fork = false
}
        '''
    }
}
