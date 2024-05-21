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
package org.gradle.api.tasks.testing.testng

import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.util.TestUtil
import spock.lang.Specification

class TestNGOptionsTest extends Specification {

    def layout = Stub(ProjectLayout) {
        getProjectDirectory() >> Stub(Directory) {
            getAsFile() >> new File("projectDir")
        }
    }
    TestNGOptions testngOptions  = TestUtil.newInstance(TestNGOptions, layout)

    String[] groups = ['fast', 'unit']

    def verifyDefaults() {
        expect:
        with(testngOptions) {
            includeGroups.empty
            excludeGroups.empty
            listeners.empty
            parallel == null
            threadCount == -1
            suiteThreadPoolSize == 1
            suiteName == 'Gradle suite'
            testName == 'Gradle test'
            configFailurePolicy == DEFAULT_CONFIG_FAILURE_POLICY
            !preserveOrder
            !groupByInstances
            !threadPoolFactoryClass
        }
    }

    def testIncludeGroups() {
        when:
        testngOptions.includeGroups(groups)

        then:
        testngOptions.includeGroups == groups as Set
        testngOptions.excludeGroups.empty
    }

    def testExcludeGroups() {
        when:
        testngOptions.excludeGroups(groups)

        then:
        testngOptions.excludeGroups == groups as Set
        testngOptions.includeGroups.empty
    }

    def copyFromOverridesOldOptions() {
        given:
        def source = testNGOptionsWithPrefix("source", false, 0)

        when:
        def target = testNGOptionsWithPrefix("target", true, 5)
        target.copyFrom(source)

        then:
        with(target) {
            outputDirectory == source.outputDirectory
            includeGroups == source.includeGroups
            excludeGroups == source.excludeGroups
            configFailurePolicy == source.configFailurePolicy
            listeners == source.listeners
            parallel == source.parallel
            threadCount == source.threadCount
            suiteThreadPoolSize == source.suiteThreadPoolSize
            useDefaultListeners == source.useDefaultListeners
            threadPoolFactoryClass == source.threadPoolFactoryClass
            suiteName == source.suiteName
            testName == source.testName
            suiteXmlFiles == source.suiteXmlFiles
            preserveOrder == source.preserveOrder
            groupByInstances == source.groupByInstances
        }
    }

    private TestNGOptions testNGOptionsWithPrefix(String prefix, boolean booleanValue, int intValue) {
        return TestUtil.newInstance(TestNGOptions, layout)
        .tap {
            setOutputDirectory(new File(prefix + "OutputDirectory"))
            setIncludeGroups([prefix + "IncludedGroup"] as Set)
            setExcludeGroups([prefix + "ExcludedGroup"] as Set)
            setConfigFailurePolicy(prefix + "ConfigFailurePolicy")
            setListeners([prefix + "Listener"] as Set)
            setParallel(prefix + "Parallel")
            setThreadCount(intValue)
            setSuiteThreadPoolSize(intValue)
            setUseDefaultListeners(booleanValue)
            setThreadPoolFactoryClass(prefix + "ThreadPoolFactoryClass")
            setSuiteName(prefix + "SuiteName")
            setTestName(prefix + "TestName")
            setSuiteXmlFiles([new File(prefix + "SuiteXmlFile")])
            setPreserveOrder(booleanValue)
            setGroupByInstances(booleanValue)
        }
    }
}
