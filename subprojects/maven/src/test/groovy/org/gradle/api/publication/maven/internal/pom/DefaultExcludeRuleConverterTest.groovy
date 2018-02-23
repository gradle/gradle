/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.publication.maven.internal.pom

import org.apache.maven.model.Exclusion
import org.gradle.api.internal.artifacts.DefaultExcludeRule
import spock.lang.Specification
import spock.lang.Unroll

class DefaultExcludeRuleConverterTest extends Specification {

    private static final String TEST_ORG = 'org'
    private static final String TEST_MODULE = 'module'

    DefaultExcludeRuleConverter excludeRuleConverter = new DefaultExcludeRuleConverter()

    @Unroll
    def "can convert exclude rule for org '#excludeRuleOrg' and module '#excludeRuleModule'"() {
        when:
        DefaultExcludeRule excludeRule = new DefaultExcludeRule(excludeRuleOrg, excludeRuleModule)
        Exclusion mavenExclude = excludeRuleConverter.convert(excludeRule)

        then:
        mavenExclude.groupId == mavenExcludeGroupId
        mavenExclude.artifactId == mavenExcludeArtifactId

        where:
        excludeRuleOrg | excludeRuleModule | mavenExcludeGroupId | mavenExcludeArtifactId
        TEST_ORG       | TEST_MODULE       | TEST_ORG            | TEST_MODULE
        TEST_ORG       | null              | TEST_ORG            | '*'
        null           | TEST_MODULE       | '*'                 | TEST_MODULE
    }

    def "cannot convert exclude rule for null org and module attributes"() {
        expect:
        !excludeRuleConverter.convert(new DefaultExcludeRule(null, null))
    }
}
