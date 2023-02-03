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

package org.gradle.api.internal.artifacts.ivyservice

import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import spock.lang.Specification

class DefaultUnresolvedDependencySpec extends Specification {

    def "provides module details"() {
        when:
        def module = DefaultModuleIdentifier.newId("org.foo", "foo")
        def dep = new DefaultUnresolvedDependency(DefaultModuleVersionSelector.newSelector(module, '1.0'), new RuntimeException("boo!"))

        then:
        dep.selector.group == 'org.foo'
        dep.selector.name == 'foo'
        dep.selector.version == '1.0'
        dep.toString() == 'org.foo:foo:1.0'
    }
}
