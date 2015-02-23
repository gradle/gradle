/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine

import org.apache.ivy.core.module.descriptor.DefaultExcludeRule
import org.apache.ivy.core.module.descriptor.ExcludeRule
import org.apache.ivy.plugins.matcher.ExactPatternMatcher
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.internal.component.model.ComponentResolveMetaData.MetaDataOrigin.*

class DefaultModuleResolutionFilterFactoryTest extends Specification {
    @Unroll
    def "can create instance for origin #origin"() {
        when:
        new DefaultModuleResolutionFilterFactory(origin)

        then:
        noExceptionThrown()

        where:
        origin << [Ivy, Maven, Gradle]
    }

    def "cannot create instance for null origin"() {
        when:
        new DefaultModuleResolutionFilterFactory(null)

        then:
        Throwable t = thrown(AssertionError)
        t.message == "Unsupported origin 'null'"
    }

    @Unroll
    def "can create accept all filter for origin #origin"() {
        when:
        ModuleResolutionFilter moduleResolutionFilter = new DefaultModuleResolutionFilterFactory(origin).all()

        then:
        moduleResolutionFilter.class == DefaultModuleResolutionFilter.AcceptAllSpec

        where:
        origin << [Ivy, Maven, Gradle]
    }

    @Unroll
    def "can create exclude any filter for origin #origin and exclude rules #excludeRules"() {
        when:
        ModuleResolutionFilter moduleResolutionFilter = new DefaultModuleResolutionFilterFactory(origin).excludeAny(excludeRules)

        then:
        moduleResolutionFilter.class == filterType

        where:
        origin | excludeRules                     | filterType
        Ivy    | []                               | DefaultModuleResolutionFilter.AcceptAllSpec
        Ivy    | [excludeRule()]                  | DefaultModuleResolutionFilter.ExcludeRuleBackedSpec
        Ivy    | [] as ExcludeRule[]              | DefaultModuleResolutionFilter.AcceptAllSpec
        Ivy    | [excludeRule()] as ExcludeRule[] | DefaultModuleResolutionFilter.ExcludeRuleBackedSpec
        Maven  | []                               | DefaultModuleResolutionFilter.AcceptAllSpec
        Maven  | [excludeRule()]                  | DefaultModuleResolutionFilter.ExcludeRuleBackedSpec
        Maven  | [] as ExcludeRule[]              | DefaultModuleResolutionFilter.AcceptAllSpec
        Maven  | [excludeRule()] as ExcludeRule[] | DefaultModuleResolutionFilter.ExcludeRuleBackedSpec
        Gradle | []                               | DefaultModuleResolutionFilter.AcceptAllSpec
        Gradle | [excludeRule()]                  | DefaultModuleResolutionFilter.ExcludeRuleBackedSpec
        Gradle | [] as ExcludeRule[]              | DefaultModuleResolutionFilter.AcceptAllSpec
        Gradle | [excludeRule()] as ExcludeRule[] | DefaultModuleResolutionFilter.ExcludeRuleBackedSpec
    }

    ExcludeRule excludeRule() {
        new DefaultExcludeRule(IvyUtil.createArtifactId('org', 'company', '*', '*', '*'), ExactPatternMatcher.INSTANCE, [:])
    }
}
