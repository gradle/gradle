/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.performance.fixture

class TestProjects {
    static void validateTestProject(String testProject) {
        File gradlePropertiesFile = new File(TestProjectLocator.findProjectDir(testProject), "gradle.properties")
        if (!gradlePropertiesFile.exists()) {
            throw new IllegalArgumentException("Every test project needs to provide a gradle.properties file with memory and parallelism settings")
        }
        def gradleProperties = new Properties()
        gradlePropertiesFile.withInputStream {gradleProperties.load(it) }
        verifyGradlePropertiesSettingSpecified(gradleProperties, "org.gradle.jvmargs")
        def jvmArgs = gradleProperties.getProperty("org.gradle.jvmargs")?.split(' ')
        if (!jvmArgs.find { it.startsWith("-Xmx") }) {
            throw new IllegalArgumentException("Test project needs to specify -Xmx in gradle.properties. org.gradle.jvmargs = ${jvmArgs?.join(' ')}")
        }
        verifyGradlePropertiesSettingSpecified(gradleProperties, "org.gradle.parallel")
        verifyGradlePropertiesSettingSpecified(gradleProperties, "org.gradle.workers.max")
    }

    private static void verifyGradlePropertiesSettingSpecified(Properties gradleProperties, String propertyName) {
        def propertyValue = gradleProperties.getProperty(propertyName)
        if (propertyValue == null || propertyValue.isEmpty()) {
            throw new IllegalArgumentException("Test project needs to specify ${propertyName} but did not.")
        }
    }

    static <T extends TestProject> T projectFor(String testProject) {
        (AndroidTestProject.findProjectFor(testProject) ?:
            JavaTestProject.projectFor(testProject)) as T
    }
}
