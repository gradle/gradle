/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.internal

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.LenientConfiguration
import org.gradle.api.artifacts.ResolvedConfiguration
import spock.lang.Specification

class IdeDependenciesExtractorTest extends Specification {
    final IdeDependenciesExtractor extractor = new IdeDependenciesExtractor()
    final Configuration configuration = Mock()
    final ResolvedConfiguration resolvedConfiguration = Mock()
    final LenientConfiguration lenientConfiguration = Mock()

    def "returns dependency entries in the order they were resolved in"() {
        given:
        def actualDependencies = [module('a'), module('b'), module('c'), module('d'), module('z')] as LinkedHashSet
        configuration.resolvedConfiguration >> resolvedConfiguration
        resolvedConfiguration.lenientConfiguration >> lenientConfiguration
        lenientConfiguration.getFiles(_) >> actualDependencies
        lenientConfiguration.unresolvedModuleDependencies >> []

        when:
        def dependencies = extractor.extractRepoFileDependencies(Mock(ConfigurationContainer), [configuration], [], false, false)

        then:
        assert dependencies.collect {it.file.name} == ['a', 'b', 'c', 'd', 'z']
    }

    def module(String name) {
        return new File(name);
    }
}
