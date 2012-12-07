/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations

import org.gradle.api.internal.artifacts.ivyservice.forcing.DefaultDependencyResolveDetails
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

/**
 * by Szczepan Faber, created at: 11/29/12
 */
class ModuleForcingResolveActionSpec extends Specification {

    def "forces modules"() {
        given:
        def forceModule1 = newSelector("org",  "module1", "1.0")
        def forceModule2 = newSelector("org",  "module2", "1.0")

        when:
        def details = new DefaultDependencyResolveDetails(requested)
        new ModuleForcingResolveAction([forceModule1, forceModule2]).execute(details)

        then:
        details.forcedVersion == forcedVersion

        where:
        requested                              | forcedVersion
        newSelector("org",  "module2", "0.9")  | "1.0"
        newSelector("orgX", "module2", "0.9")  | null
        newSelector("org",  "moduleX", "0.9")  | null
    }

    def "does not force anything when input empty"() {
        def details = new DefaultDependencyResolveDetails(newSelector("org",  "foo", "1.0"))
        when:
        new ModuleForcingResolveAction([]).execute(details)

        then:
        details.forcedVersion == null
    }
}
