/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.notations


import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.internal.typeconversion.NotationConvertResult
import org.gradle.internal.typeconversion.NotationConverter
import org.gradle.plugin.use.PluginDependency
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DependencyPluginNotationConverterTest extends Specification {
    final NotationConverter<MinimalExternalModuleDependency, DefaultExternalModuleDependency> minimalConverter = Mock()

    @Subject converter = new DependencyPluginNotationConverter(minimalConverter)

    @Unroll("Parses plugin dependency #pluginId:#version")
    def "converts plugin dependency and delegates to minimalConverter"() {
        final PluginDependency pluginDependency = Mock()
        final NotationConvertResult<? super DefaultExternalModuleDependency> result = Mock()

        when:
        converter.convert(pluginDependency, result)

        then:
        1 * minimalConverter.convert({
            it.module.group == pluginId &&
                it.module.name == "${pluginId}.gradle.plugin" &&
                it.versionConstraint.requiredVersion == (version ?: '')
        }, result)

        and:
        pluginDependency.pluginId >> pluginId
        pluginDependency.version >> DefaultImmutableVersionConstraint.of((String) version)

        where:
        pluginId | version
        'java'   | '1.0'
        'java'   | null
    }

}
