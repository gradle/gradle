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

package org.gradle.integtests.tooling.r76

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildLauncher
import org.gradle.util.GradleVersion

@ToolingApiVersion('>=7.6')
@TargetGradleVersion('>=7.6')
class SystemPropertyPropagationCrossVersionTest extends ToolingApiSpecification {

    OutputStream out

    def setup() {
        out = new ByteArrayOutputStream()
        System.setProperty('mySystemProperty', 'defined in the client JVM')
        buildFile << '''
            tasks.register('printSystemProperty') {
                doLast {
                    System.properties.each { k, v ->
                        println("$k=$v")
                    }
                }
            }
        '''
    }

    @TargetGradleVersion(">=2.6 <7.6")
    def "Custom system properties are ignored in older Gradle versions"() {
        setup:
        if (targetDist.version < GradleVersion.version('4.9') ) {
            buildFile.text = buildFile.text.replace('tasks.register', 'tasks.create')
        }

        when:
        runTask { withSystemProperties('mySystemProperty' : 'ignored') }

        then:
        hasSystemProperty('mySystemProperty', 'defined in the client JVM')
    }

    def "Client JVM system properties appear in the build if withSystemProperties() is not called"() {
        when:
        runTask()

        then:
        hasSystemProperty('mySystemProperty', 'defined in the client JVM')
    }

    def "Client JVM system properties do not appear in the build if withSystemProperties() is called"() {
        setup:
        toolingApi.requireDaemons() // no separate daemon JVM -> all client JVM system properties are expected to be visible

        when:
        runTask { withSystemProperties('unrelated' : 'value') }

        then:
        hasNoSystemProperty('mySystemProperty')
    }

    def "Calling withSystemProperties(null) resets to default behavior"() {
        when:
        runTask {
            withSystemProperties('unrelated' : 'value')
            withSystemProperties(null)
        }

        then:
        hasSystemProperty('mySystemProperty', 'defined in the client JVM')
    }

    def "Passing an empty map to withSystemProperties() hides all client system properties"() {
        setup:
        toolingApi.requireDaemons() // no separate daemon JVM -> all client JVM system properties are expected to be visible

        when:
        runTask { withSystemProperties([:]) }

        then:
        hasNoSystemProperty('mySystemProperty')
    }

    def "Can define new system property"() {
        when:
        runTask { withSystemProperties('customKey' : 'customValue') }

        then:
        hasSystemProperty('customKey', 'customValue')
    }

    def "Can override existing system properties"() {
        when:
        runTask { withSystemProperties('mySystemProperty' : 'newValue') }

        then:
        hasSystemProperty('mySystemProperty', 'newValue')
    }

    def "JVM arguments have precedence over system properties"() {
        when:
        runTask {
            withSystemProperties('customKey' : 'syspropValue')
            addJvmArguments('-DcustomKey=jvmargValue')
        }

        then:
        hasSystemProperty('customKey', 'jvmargValue')
    }

    def "Cannot modify immutable system properties"() {
        setup:
        toolingApi.requireDaemons() // no separate daemon JVM -> no immutable system properties

        when:
        String customTmpDir = System.getProperty('java.io.tmpdir') + System.getProperty('path.separator') + 'custom'
        runTask {
            withSystemProperties('java.io.tmpdir' : customTmpDir)
        }

        then:
        hasNoSystemProperty('java.io.tmpdir', customTmpDir)
    }

    private void runTask(@DelegatesTo(value = BuildLauncher, strategy = Closure.DELEGATE_ONLY) Closure<?> launcherSpec = {}) {
        withConnection {
            def launcher = newBuild().forTasks('printSystemProperty')
            launcherSpec.delegate = launcher
            launcherSpec()
            launcher.setStandardOutput(out)
            launcher.run()
        }
    }

    private boolean hasNoSystemProperty(String key, String value = null) {
        !out.toString().contains("$key=${value ?: ""}")
    }

    private boolean hasSystemProperty(String key, String value) {
        out.toString().contains("$key=$value")
    }
}
