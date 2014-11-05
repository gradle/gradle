/*
 * Copyright 2014 the original author or authors.
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
import org.apache.ivy.plugins.matcher.PatternMatcher
import org.apache.ivy.plugins.matcher.RegexpPatternMatcher
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.ivyservice.IvyUtil.createArtifactId

class ArtifactIdSpecTest extends Specification {
    def "accepts all artifacts by default"() {
        def spec = ArtifactIdSpec.forExcludes()

        expect:
        spec.isSatisfiedBy(createArtifactId("org", "module", "test", "jar", "jar"))
    }

    def "does not accept artifact that matches any exclude rule"() {
        def rule1 = excludeAnyArtifactsRule("org", "module")
        def rule2 = excludeAnyArtifactsRule("org", "module2")
        def rule3 = excludeAnyArtifactsRule("org2", "*")
        def rule4 = excludeAnyArtifactsRule("*", "module4")
        def rule5 = regExpExcludeAnyArtifactsRule("regexp-\\d+", "module\\d+")
        def spec = ArtifactIdSpec.forExcludes(rule1, rule2, rule3, rule4, rule5)

        expect:
        !spec.isSatisfiedBy(createArtifactId("org", "module", "a", "jar", "jar"))
        !spec.isSatisfiedBy(createArtifactId("org", "module2", "b", "jar", "jar"))
        !spec.isSatisfiedBy(createArtifactId("org2", "anything", "c", "jar", "jar"))
        !spec.isSatisfiedBy(createArtifactId("other", "module4", "d", "jar", "jar"))
        !spec.isSatisfiedBy(createArtifactId("regexp-72", "module12", "e", "jar", "jar"))
        spec.isSatisfiedBy(createArtifactId("org", "other", "f", "jar", "jar"))
        spec.isSatisfiedBy(createArtifactId("regexp-72", "other", "g", "jar", "jar"))
        spec.isSatisfiedBy(createArtifactId("regexp", "module2", "h", "jar", "jar"))
    }

    def "does not accept artifact that matches specific exclude rule"() {
        def rule1 = excludeAnyModuleArtifactsRule("a", "jar", "jar")
        def rule2 = excludeAnyModuleArtifactsRule("b", "jar", "jar")
        def rule3 = excludeAnyModuleArtifactsRule("c", "*", "*")
        def rule4 = excludeAnyModuleArtifactsRule("d", "*", "jar")
        def rule5 = excludeAnyModuleArtifactsRule("e", "sources", "jar")
        def rule6 = excludeAnyModuleArtifactsRule("f", "sources", "*")
        def rule7 = excludeAnyModuleArtifactsRule("g", "jar", "war")
        def rule8 = regExpExcludeAnyModuleArtifactsRule("regexp-\\d+", "jar", "jar")
        def spec = ArtifactIdSpec.forExcludes(rule1, rule2, rule3, rule4, rule5, rule6, rule7, rule8)

        expect:
        !spec.isSatisfiedBy(createArtifactId("org", "module", "a", "jar", "jar"))
        !spec.isSatisfiedBy(createArtifactId("org", "module2", "b", "jar", "jar"))
        !spec.isSatisfiedBy(createArtifactId("org2", "anything", "c", "jar", "jar"))
        !spec.isSatisfiedBy(createArtifactId("other", "module4", "d", "jar", "jar"))
        !spec.isSatisfiedBy(createArtifactId("some", "app", "e", "sources", "jar"))
        !spec.isSatisfiedBy(createArtifactId("foo", "bar", "f", "sources", "jar"))
        !spec.isSatisfiedBy(createArtifactId("well", "known", "g", "jar", "war"))
        !spec.isSatisfiedBy(createArtifactId("other", "sample", "regexp-99", "jar", "jar"))
        spec.isSatisfiedBy(createArtifactId("some", "app", "e", "jar", "jar"))
        spec.isSatisfiedBy(createArtifactId("some", "app", "e", "javadoc", "jar"))
        spec.isSatisfiedBy(createArtifactId("foo", "bar", "f", "jar", "jar"))
        spec.isSatisfiedBy(createArtifactId("well", "known", "g", "jar", "jar"))
        spec.isSatisfiedBy(createArtifactId("well", "known", "g", "jar", "zip"))
        spec.isSatisfiedBy(createArtifactId("other", "sample", "regexp", "jar", "jar"))
    }

    def "union accepts all artifacts when one spec has empty set of exclude rules"() {
        def rule1 = excludeAnyModuleArtifactsRule("a", "jar", "jar")
        def rule2 = excludeAnyModuleArtifactsRule("b", "jar", "jar")
        def spec = ArtifactIdSpec.forExcludes(rule1, rule2)
        def spec2 = ArtifactIdSpec.forExcludes()

        expect:
        spec.union(spec2) == spec2
        spec2.union(spec) == spec2
    }

    def "union of a spec with itself returns the original spec"() {
        def rule1 = excludeAnyModuleArtifactsRule("a", "jar", "jar")
        def rule2 = excludeAnyModuleArtifactsRule("b", "jar", "jar")
        def spec = ArtifactIdSpec.forExcludes(rule1, rule2)

        expect:
        spec.union(spec) == spec
    }

    def "intersection accepts those artifacts accepted by other spec when one spec has empty set of exclude rules"() {
        def rule1 = excludeAnyModuleArtifactsRule("a", "jar", "jar")
        def rule2 = excludeAnyModuleArtifactsRule("b", "jar", "jar")
        def spec = ArtifactIdSpec.forExcludes(rule1, rule2)
        def spec2 = ArtifactIdSpec.forExcludes()

        expect:
        spec.intersect(spec2) == spec
        spec2.intersect(spec) == spec
    }

    def "intersection of a spec with itself returns the original spec"() {
        def rule1 = excludeAnyModuleArtifactsRule("a", "jar", "jar")
        def rule2 = excludeAnyModuleArtifactsRule("b", "jar", "jar")
        def spec = ArtifactIdSpec.forExcludes(rule1, rule2)

        expect:
        spec.intersect(spec) == spec
    }

    private ExcludeRule excludeAnyArtifactsRule(String org, String module) {
        excludeArtifactsRule(org, module, PatternMatcher.ANY_EXPRESSION, PatternMatcher.ANY_EXPRESSION, PatternMatcher.ANY_EXPRESSION)
    }

    private ExcludeRule excludeAnyModuleArtifactsRule(String name, String type, String ext) {
        excludeArtifactsRule(PatternMatcher.ANY_EXPRESSION, PatternMatcher.ANY_EXPRESSION, name, type, ext)
    }

    private ExcludeRule excludeArtifactsRule(String org, String module, String name, String type, String ext) {
        new DefaultExcludeRule(createArtifactId(org, module, name, type, ext), ExactPatternMatcher.INSTANCE, [:])
    }

    private ExcludeRule regExpExcludeAnyArtifactsRule(String org, String module) {
        regExpExcludeArtifactsRule(org, module, PatternMatcher.ANY_EXPRESSION, PatternMatcher.ANY_EXPRESSION, PatternMatcher.ANY_EXPRESSION)
    }

    private ExcludeRule regExpExcludeAnyModuleArtifactsRule(String name, String type, String ext) {
        regExpExcludeArtifactsRule(PatternMatcher.ANY_EXPRESSION, PatternMatcher.ANY_EXPRESSION,  name, type, ext)
    }

    private ExcludeRule regExpExcludeArtifactsRule(String org, String module, String name, String type, String ext) {
        new DefaultExcludeRule(createArtifactId(org, module,  name, type, ext), RegexpPatternMatcher.INSTANCE, [:])
    }
}
