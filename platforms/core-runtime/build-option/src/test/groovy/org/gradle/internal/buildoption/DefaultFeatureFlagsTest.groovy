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

package org.gradle.internal.buildoption

import org.gradle.internal.event.ListenerManager
import spock.lang.Specification

class DefaultFeatureFlagsTest extends Specification {
    def sysProperties = [:]
    def featureFlagListener = Mock(FeatureFlagListener)
    def listenerManager = Stub(ListenerManager) {
        getBroadcaster(FeatureFlagListener) >> featureFlagListener
    }
    def flags = new DefaultFeatureFlags(new DefaultInternalOptions(sysProperties), listenerManager)

    def "flag is disabled by default"() {
        def flag = Stub(FeatureFlag)
        flag.systemPropertyName >> null

        expect:
        !flags.isEnabled(flag)
        !flags.isEnabledWithApi(flag)
    }

    def "flag with associated system property is disabled by default"() {
        def flag = Stub(FeatureFlag)
        flag.systemPropertyName >> "prop"

        expect:
        !flags.isEnabled(flag)
        !flags.isEnabledWithApi(flag)
    }

    def "can explicitly enable flag"() {
        def flag = Stub(FeatureFlag)
        flag.systemPropertyName >> null
        flags.enable(flag)

        expect:
        flags.isEnabled(flag)
        flags.isEnabledWithApi(flag)
    }

    def "can explicitly enable flag with associated system property"() {
        def flag = Stub(FeatureFlag)
        flag.systemPropertyName >> "prop"
        flags.enable(flag)

        expect:
        flags.isEnabled(flag)
        flags.isEnabledWithApi(flag)
    }

    def "can use a system property to enable flag"() {
        sysProperties["prop"] = "true"

        def flag = Stub(FeatureFlag)
        flag.systemPropertyName >> "prop"

        expect:
        flags.isEnabled(flag)
        !flags.isEnabledWithApi(flag)
    }

    def "can use a system property to disable a flag that has been enabled"() {
        sysProperties["prop"] = "false"

        def flag = Stub(FeatureFlag)
        flag.systemPropertyName >> "prop"

        flags.enable(flag)

        expect:
        !flags.isEnabled(flag)
        flags.isEnabledWithApi(flag)
    }

    def "querying flag status notifies listener"() {
        def flag = Stub(FeatureFlag)
        flag.systemPropertyName >> propertyName

        when:
        flags.isEnabled(flag)

        then:
        1 * featureFlagListener.flagRead(flag)

        where:
        propertyName << ["prop", null]
    }
}
