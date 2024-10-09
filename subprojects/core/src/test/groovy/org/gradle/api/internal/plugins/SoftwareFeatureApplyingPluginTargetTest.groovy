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

package org.gradle.api.internal.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.plugin.software.internal.SoftwareFeatureApplicator
import org.gradle.plugin.software.internal.SoftwareTypeImplementation
import org.gradle.plugin.software.internal.SoftwareTypeRegistry
import spock.lang.Specification

class SoftwareFeatureApplyingPluginTargetTest extends Specification {
    def target = Mock(Project)
    def delegate = Mock(PluginTarget)
    def softwareTypeRegistry = Mock(SoftwareTypeRegistry)
    def softwareFeatureApplicator = Mock(SoftwareFeatureApplicator)
    def plugin = Mock(Plugin)
    def softwareTypeImplementation = Mock(SoftwareTypeImplementation)
    def pluginTarget = new SoftwareFeatureApplyingPluginTarget(target, delegate, softwareTypeRegistry, softwareFeatureApplicator)

    def "applies software feature to target"() {
        when:
        pluginTarget.applySoftwareFeatures(plugin)

        then:
        1 * softwareTypeRegistry.implementationFor(plugin.getClass()) >> Optional.of(softwareTypeImplementation)
        1 * softwareFeatureApplicator.applyFeatureTo(target, softwareTypeImplementation)
    }

    def "does not apply software feature when plugin is not in registry"() {
        when:
        pluginTarget.applySoftwareFeatures(plugin)

        then:
        1 * softwareTypeRegistry.implementationFor(plugin.getClass()) >> Optional.empty()
        0 * softwareFeatureApplicator.applyFeatureTo(_, _)
    }

    def "delegates to target for other methods"() {
        when:
        pluginTarget.applyImperative("com.test.plugin", plugin)

        then:
        1 * delegate.applyImperative("com.test.plugin", plugin)

        when:
        pluginTarget.applyRules("com.test.plugin", plugin.getClass())

        then:
        1 * delegate.applyRules("com.test.plugin", plugin.getClass())

        when:
        pluginTarget.applyImperativeRulesHybrid("com.test.plugin", plugin, String.class)

        then:
        1 * delegate.applyImperativeRulesHybrid("com.test.plugin", plugin, String.class)
    }
}
