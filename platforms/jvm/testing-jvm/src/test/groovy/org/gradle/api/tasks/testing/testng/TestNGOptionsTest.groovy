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
        testngOptions.includeGroups.get().empty
        testngOptions.excludeGroups.get().empty
        testngOptions.listeners.get().empty
        testngOptions.parallel.getOrNull() == null
        testngOptions.threadCount.get() == -1
        testngOptions.suiteThreadPoolSize.get() == 1
        testngOptions.suiteName.get() == 'Gradle suite'
        testngOptions.testName.get() == 'Gradle test'
        testngOptions.configFailurePolicy.get() == TestNGOptions.DEFAULT_CONFIG_FAILURE_POLICY
        !testngOptions.preserveOrder.get()
        !testngOptions.groupByInstances.get()
        testngOptions.threadPoolFactoryClass.getOrNull() == null
    }

    def testIncludeGroups() {
        when:
        testngOptions.includeGroups(groups)

        then:
        testngOptions.includeGroups.get() == groups as Set
        testngOptions.excludeGroups.get().empty
    }

    def testExcludeGroups() {
        when:
        testngOptions.excludeGroups(groups)

        then:
        testngOptions.excludeGroups.get() == groups as Set
        testngOptions.includeGroups.get().empty
    }

    def copyFromOverridesOldOptions() {
        given:
        def source = testNGOptionsWithPrefix("source", false, 0)

        when:
        def target = testNGOptionsWithPrefix("target", true, 5)
        target.copyFrom(source)

        then:
        target.outputDirectory.get() == source.outputDirectory.get()
        target.includeGroups.get() == source.includeGroups.get()
        target.excludeGroups.get() == source.excludeGroups.get()
        target.configFailurePolicy.get() == source.configFailurePolicy.get()
        target.listeners.get() == source.listeners.get()
        target.parallel.get() == source.parallel.get()
        target.threadCount.get() == source.threadCount.get()
        target.suiteThreadPoolSize.get() == source.suiteThreadPoolSize.get()
        target.useDefaultListeners.get() == source.useDefaultListeners.get()
        target.threadPoolFactoryClass.get() == source.threadPoolFactoryClass.get()
        target.suiteName.get() == source.suiteName.get()
        target.testName.get() == source.testName.get()
        target.suiteXmlFiles.files == source.suiteXmlFiles.files
        target.preserveOrder.get() == source.preserveOrder.get()
        target.groupByInstances.get() == source.groupByInstances.get()
    }

    private TestNGOptions testNGOptionsWithPrefix(String prefix, boolean booleanValue, int intValue) {
        TestNGOptions options = TestUtil.newInstance(TestNGOptions, layout)
        options.outputDirectory = new File(prefix + "OutputDirectory")
        options.includeGroups = [prefix + "IncludedGroup"] as Set
        options.excludeGroups = [prefix + "ExcludedGroup"] as Set
        options.configFailurePolicy = prefix + "ConfigFailurePolicy"
        options.listeners = [prefix + "Listener"] as Set
        options.parallel = prefix + "Parallel"
        options.threadCount = intValue
        options.suiteThreadPoolSize = intValue
        options.useDefaultListeners = booleanValue
        options.threadPoolFactoryClass = prefix + "ThreadPoolFactoryClass"
        options.suiteName = prefix + "SuiteName"
        options.testName = prefix + "TestName"
        options.suiteXmlFiles.setFrom(new File(prefix + "SuiteXmlFile").absoluteFile)
        options.preserveOrder = booleanValue
        options.groupByInstances = booleanValue
        return options
    }
}
