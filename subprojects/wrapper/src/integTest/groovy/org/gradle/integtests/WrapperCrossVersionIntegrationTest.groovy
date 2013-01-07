/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.integtests

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleDistribution

class WrapperCrossVersionIntegrationTest extends CrossVersionIntegrationSpec {
    public void canUseWrapperFromPreviousVersionToRunCurrentVersion() {
        expect:
        checkWrapperWorksWith(previous, current)
    }

    public void canUseWrapperFromCurrentVersionToRunPreviousVersion() {
        expect:
        checkWrapperWorksWith(current, previous)
    }

    void checkWrapperWorksWith(GradleDistribution wrapperGenVersion, GradleDistribution executionVersion) {
        if (!wrapperGenVersion.wrapperCanExecute(executionVersion.version)) {
            println "skipping $wrapperGenVersion as its wrapper cannot execute version ${executionVersion.version}"
            return
        }

        println "use wrapper from $wrapperGenVersion to build using $executionVersion"

        buildFile << """

task wrapper(type: Wrapper) {
    gradleVersion = '$executionVersion.version'
}

//(SF) not sure if we want to keep coverage for old 'urlRoot' that was already removed
//I'm keeping it so that old versions are tested via the urlRoot.
if (wrapper.hasProperty('urlRoot')) {
    println "configuring the wrapper using the old way: 'urlRoot'..."
    wrapper.urlRoot = '${executionVersion.binDistribution.parentFile.toURI()}'
} else {
    println "configuring the wrapper using the new way: 'distributionUrl'..."
    wrapper.distributionUrl = '${executionVersion.binDistribution.toURI()}'
}

println "using Java version \${System.getProperty('java.version')}"

task hello {
    doLast { println "hello from \$gradle.gradleVersion" }
}
"""
        version(wrapperGenVersion).withTasks('wrapper').run()
        def result = version(wrapperGenVersion).usingExecutable('gradlew').withDeprecationChecksDisabled().withTasks('hello').run()
        assert result.output.contains("hello from $executionVersion.version")
    }
}

