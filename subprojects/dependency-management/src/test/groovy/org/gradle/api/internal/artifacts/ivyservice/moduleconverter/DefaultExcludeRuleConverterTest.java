/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter;

import org.apache.ivy.plugins.matcher.ExactPatternMatcher;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.util.WrapUtil;
import org.hamcrest.Matchers;
import org.junit.Test;

import static org.junit.Assert.assertThat;

public class DefaultExcludeRuleConverterTest {

    @Test
    public void testCreateExcludeRule() {
        String configurationName = "someConf";
        final String someOrg = "someOrg";
        final String someModule = "someModule";
        org.apache.ivy.core.module.descriptor.ExcludeRule ivyExcludeRule =
                new DefaultExcludeRuleConverter().createExcludeRule(configurationName, new DefaultExcludeRule(someOrg, someModule));
        assertThat(ivyExcludeRule.getId().getModuleId().getOrganisation(),
                Matchers.equalTo(someOrg));
        assertThat(ivyExcludeRule.getId().getName(),
                Matchers.equalTo(PatternMatcher.ANY_EXPRESSION));
        assertThat(ivyExcludeRule.getId().getExt(),
                Matchers.equalTo(PatternMatcher.ANY_EXPRESSION));
        assertThat(ivyExcludeRule.getId().getType(),
                Matchers.equalTo(PatternMatcher.ANY_EXPRESSION));
        assertThat((ExactPatternMatcher) ivyExcludeRule.getMatcher(),
                Matchers.equalTo(ExactPatternMatcher.INSTANCE));
        assertThat(ivyExcludeRule.getConfigurations(),
                Matchers.equalTo(WrapUtil.toArray(configurationName)));
    }
}
