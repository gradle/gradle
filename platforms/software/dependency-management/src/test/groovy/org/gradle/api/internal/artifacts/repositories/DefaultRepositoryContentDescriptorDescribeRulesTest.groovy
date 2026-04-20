/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import spock.lang.Specification

class DefaultRepositoryContentDescriptorDescribeRulesTest extends Specification {
    def descriptor = new DefaultRepositoryContentDescriptor({ "test" }, new VersionParser())

    def "describes includeGroup as includeGroup(\"g\")"() {
        when:
        descriptor.includeGroup("com.example")

        then:
        descriptor.describeIncludeRules() == ['includeGroup("com.example")']
        descriptor.describeExcludeRules() == []
    }

    def "describes includeGroupAndSubgroups"() {
        when:
        descriptor.includeGroupAndSubgroups("com.example")

        then:
        descriptor.describeIncludeRules() == ['includeGroupAndSubgroups("com.example")']
    }

    def "describes includeGroupByRegex"() {
        when:
        descriptor.includeGroupByRegex("com\\\\.example.*")

        then:
        descriptor.describeIncludeRules() == ['includeGroupByRegex("com\\\\.example.*")']
    }

    def "describes includeModuleByRegex"() {
        when:
        descriptor.includeModuleByRegex("com\\\\.example.*", "lib.*")

        then:
        descriptor.describeIncludeRules() == ['includeModuleByRegex("com\\\\.example.*", "lib.*")']
    }

    def "describes excludeModuleByRegex distinctly from excludeModule, preserving declaration order"() {
        when:
        descriptor.excludeModule("a", "b")
        descriptor.excludeModuleByRegex("x\\\\..*", "y.*")

        then:
        descriptor.describeExcludeRules() == [
            'excludeModule("a", "b")',
            'excludeModuleByRegex("x\\\\..*", "y.*")'
        ]
    }

    def "preserves declaration order for include rules"() {
        when:
        descriptor.includeGroup("z.last")
        descriptor.includeGroup("a.first")
        descriptor.includeGroup("m.middle")

        then:
        descriptor.describeIncludeRules() == [
            'includeGroup("z.last")',
            'includeGroup("a.first")',
            'includeGroup("m.middle")'
        ]
    }

    def "preserves declaration order for exclude rules"() {
        when:
        descriptor.excludeGroup("z.last")
        descriptor.excludeGroup("a.first")
        descriptor.excludeGroup("m.middle")

        then:
        descriptor.describeExcludeRules() == [
            'excludeGroup("z.last")',
            'excludeGroup("a.first")',
            'excludeGroup("m.middle")'
        ]
    }

    def "includeModule renders identically to includeModuleByRegex because of internal storage"() {
        when:
        descriptor.includeModule("com.example", "lib")

        then:
        // Known ambiguity: includeModule stores as MatcherKind.REGEX, indistinguishable from includeModuleByRegex
        descriptor.describeIncludeRules() == ['includeModuleByRegex("com.example", "lib")']
    }

    def "describes includeVersion"() {
        when:
        descriptor.includeVersion("com.example", "lib", "1.0")

        then:
        descriptor.describeIncludeRules() == ['includeVersion("com.example", "lib", "1.0")']
    }

    def "describes includeVersionByRegex"() {
        when:
        descriptor.includeVersionByRegex("com\\\\.example.*", "lib.*", "1\\\\..*")

        then:
        descriptor.describeIncludeRules() == ['includeVersionByRegex("com\\\\.example.*", "lib.*", "1\\\\..*")']
    }

    def "describes excludeGroup, excludeModule, excludeVersion in declaration order"() {
        when:
        descriptor.excludeGroup("a")
        descriptor.excludeModule("b", "m")
        descriptor.excludeVersion("c", "m", "1.0")

        then:
        descriptor.describeIncludeRules() == []
        descriptor.describeExcludeRules() == [
            'excludeGroup("a")',
            'excludeModule("b", "m")',
            'excludeVersion("c", "m", "1.0")'
        ]
    }

    def "returns empty lists when no rules declared"() {
        expect:
        descriptor.describeIncludeRules() == []
        descriptor.describeExcludeRules() == []
    }
}
