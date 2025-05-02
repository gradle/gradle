/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.Project
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.initialization.properties.DefaultSystemPropertiesInstaller
import org.gradle.initialization.properties.SystemPropertiesInstaller
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

import static java.util.Collections.emptyMap

class DefaultSystemPropertiesInstallerTest extends Specification {

    private GradleProperties loadedGradleProperties = Mock(GradleProperties) {
        getProperties() >> [
            (Project.SYSTEM_PROP_PREFIX + ".userSystemProp"): "userSystemValue",
            (Project.SYSTEM_PROP_PREFIX + ".userSystemProp2"): "userSystemValue2",
        ]
    }
    private EnvironmentChangeTracker environmentChangeTracker = Mock(EnvironmentChangeTracker)
    private StartParameterInternal startParameter = Mock(StartParameterInternal) {
        systemPropertiesArgs >> { startParameterSystemProperties }
    }
    private GradleInternal gradle = Mock(GradleInternal)

    private Map<String, String> startParameterSystemProperties = emptyMap()

    private SystemPropertiesInstaller systemPropertiesInstaller = new DefaultSystemPropertiesInstaller(environmentChangeTracker, startParameter, gradle)

    @Rule
    public SetSystemProperties sysProp = new SetSystemProperties()

    def "set system properties"() {
        when:
        systemPropertiesInstaller.setSystemPropertiesFrom(loadedGradleProperties)

        then:
        "userSystemValue" == System.getProperty("userSystemProp")
        "userSystemValue2" == System.getProperty("userSystemProp2")
    }

    def "track loaded properties"() {
        given:
        gradle.isRootBuild() >> isRootBuild

        when:
        systemPropertiesInstaller.setSystemPropertiesFrom(loadedGradleProperties)

        then:
        if (isRootBuild) {
            0 * environmentChangeTracker.systemPropertyLoaded(_)
        } else {
            1 * environmentChangeTracker.systemPropertyLoaded("userSystemProp", "userSystemValue", null)
            1 * environmentChangeTracker.systemPropertyLoaded("userSystemProp2", "userSystemValue2", null)
        }

        where:
        isRootBuild << [true, false]
    }

    def "track override properties"() {
        given:
        startParameterSystemProperties = [("overrideSystemProp"): "overrideSystemValue"]

        when:
        systemPropertiesInstaller.setSystemPropertiesFrom(loadedGradleProperties)

        then:
        1 * environmentChangeTracker.systemPropertyOverridden("overrideSystemProp")
    }

    def "build system properties"() {
        given:
        System.setProperty("gradle-loader-test", "value")

        expect:
        System.getProperties().containsKey("gradle-loader-test")
        "value" == System.getProperties().get("gradle-loader-test")
    }
}
