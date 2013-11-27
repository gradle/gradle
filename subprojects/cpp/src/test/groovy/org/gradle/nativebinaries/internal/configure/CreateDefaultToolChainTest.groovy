/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativebinaries.internal.configure
import org.gradle.api.internal.plugins.ExtensionContainerInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.nativebinaries.internal.ToolChainRegistryInternal
import spock.lang.Specification

class CreateDefaultToolChainTest extends Specification {
    def action = new CreateDefaultToolChain()

    def project = Mock(ProjectInternal)
    def extensions = Mock(ExtensionContainerInternal)
    def toolChains = Mock(ToolChainRegistryInternal)

    def "setup"() {
        _ * project.getExtensions() >> extensions
        _ * extensions.getByType(ToolChainRegistryInternal) >> toolChains
    }

    def "adds default tool chains when none configured"() {
        when:
        action.execute(project)

        then:
        1 * toolChains.empty >> true
        1 * toolChains.addDefaultToolChains()
    }

    def "does not add default tool chains when some configured"() {
        when:
        action.execute(project)

        then:
        1 * toolChains.empty >> false
        0 * toolChains.addDefaultToolChains()
    }
}
