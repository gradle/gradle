/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.buildconfiguration.tasks

import org.gradle.api.tasks.AbstractTaskTest
import org.gradle.internal.buildconfiguration.BuildPropertiesDefaults
import org.gradle.internal.jvm.inspection.JvmVendor.KnownJvmVendor
import org.gradle.internal.util.PropertiesUtils
import org.gradle.jvm.toolchain.JvmImplementation
import org.gradle.util.internal.GFileUtils
import org.gradle.util.internal.GUtil

import static org.junit.Assert.assertEquals

class UpdateDaemonJvmTaskTest extends AbstractTaskTest {

    private UpdateDaemonJvmTask daemonJvmTask
    private File expectedBuildGradleProperties

    def setup() {
        daemonJvmTask = createTask(UpdateDaemonJvmTask.class)
        daemonJvmTask.toolchainVersion.convention(BuildPropertiesDefaults.TOOLCHAIN_VERSION)
        expectedBuildGradleProperties = new File(getProject().getProjectDir(), BuildPropertiesDefaults.BUILD_PROPERTIES_FILE)
    }

    UpdateDaemonJvmTask getTask() {
        return daemonJvmTask
    }

    def "When execute updateDaemonJvm without options Then build file has default jvm criteria"() {
        when:
        execute(daemonJvmTask)

        then:
        assertDaemonJvmProperties(BuildPropertiesDefaults.TOOLCHAIN_VERSION)
    }

    def "When execute updateDaemonJvm with different options Then build file contains the specified jvm criteria"() {
        given:
        daemonJvmTask.toolchainVersion.set(11)
        daemonJvmTask.toolchainVendor.set(KnownJvmVendor.IBM)
        daemonJvmTask.toolchainImplementation.set(JvmImplementation.VENDOR_SPECIFIC)

        when:
        execute(daemonJvmTask)

        then:
        assertDaemonJvmProperties(11, KnownJvmVendor.IBM, JvmImplementation.VENDOR_SPECIFIC)
    }

    def "Given already existing build properties When execute updateDaemonJvm with different criteria Then criteria get modified but the other build properties are still present"() {
        given:
        def initialProperties = new Properties()
        initialProperties.setProperty("test.key", "testValue")
        initialProperties.setProperty(BuildPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY, "20")
        initialProperties.setProperty(BuildPropertiesDefaults.TOOLCHAIN_IMPLEMENTATION_PROPERTY, "J9")
        GFileUtils.parentMkdirs(expectedBuildGradleProperties)
        PropertiesUtils.store(initialProperties, expectedBuildGradleProperties)

        when:
        daemonJvmTask.toolchainVersion.set(17)
        daemonJvmTask.toolchainVendor.set(KnownJvmVendor.ADOPTIUM)
        execute(daemonJvmTask)

        then:
        assertDaemonJvmProperties(17, KnownJvmVendor.ADOPTIUM)
        def resultProperties = GUtil.loadProperties(expectedBuildGradleProperties)
        resultProperties.getProperty("test.key") == "testValue"
    }

    private def assertDaemonJvmProperties(Integer version, KnownJvmVendor vendor = null, JvmImplementation implementation = null) {
        assertEquals(version, daemonJvmTask.toolchainVersion.get())
        assertEquals(vendor, daemonJvmTask.toolchainVendor.getOrNull())
        assertEquals(implementation, daemonJvmTask.toolchainImplementation.getOrNull())

        def properties = GUtil.loadProperties(expectedBuildGradleProperties)
        assertEquals(version.toString(), properties.getProperty(BuildPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY))
        assertEquals(vendor?.name(), properties.getProperty(BuildPropertiesDefaults.TOOLCHAIN_VENDOR_PROPERTY))
        assertEquals(implementation?.name(), properties.getProperty(BuildPropertiesDefaults.TOOLCHAIN_IMPLEMENTATION_PROPERTY))

        return true
    }
}
