/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.integtests.tooling.r25

import org.gradle.integtests.tooling.fixture.ContinuousBuildToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersions
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Timeout

@Timeout(60)
@Requires(TestPrecondition.JDK7_OR_LATER)
@ToolingApiVersion(ToolingApiVersions.SUPPORTS_CANCELLATION)
class ContinuousBuildClientCompatibilityCrossVersionSpec extends ContinuousBuildToolingApiSpecification {

    def "all tooling API clients that support cancellation (>=2.1) can run continuous build"() {
        given:
        def javaSrcDir = setupJavaProject()
        def javaSrcFile = javaSrcDir.file("Thing.java")
        javaSrcFile << 'public class Thing {}'

        when:
        succeeds('build')

        then:
        executedAndNotSkipped ":compileJava", ":build"

        when:
        javaSrcFile.text = 'public class Thing { *******'

        then:
        fails()

        when:
        javaSrcFile.text = 'public class Thing {} '

        then:
        succeeds()
        executedAndNotSkipped ":compileJava"
    }
}
