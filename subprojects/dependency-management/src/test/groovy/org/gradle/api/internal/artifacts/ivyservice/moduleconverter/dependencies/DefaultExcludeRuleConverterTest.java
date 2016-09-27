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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers;
import org.gradle.internal.component.model.Exclude;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertThat;

public class DefaultExcludeRuleConverterTest {

    @Test
    public void testCreateExcludeRule() {
        String configurationName = "someConf";
        final String someOrg = "someOrg";
        final String someModule = "someModule";
        Exclude exclude =
                new DefaultExcludeRuleConverter().convertExcludeRule(configurationName, new DefaultExcludeRule(someOrg, someModule));
        assertThat(exclude.getModuleId().getGroup(),
                Matchers.equalTo(someOrg));
        assertThat(exclude.getModuleId().getName(),
                Matchers.equalTo(someModule));
        assertThat(exclude.getArtifact().getExtension(),
                Matchers.equalTo(PatternMatchers.ANY_EXPRESSION));
        assertThat(exclude.getArtifact().getType(),
                Matchers.equalTo(PatternMatchers.ANY_EXPRESSION));
        assertThat(exclude.getMatcher(),
                Matchers.equalTo(PatternMatchers.EXACT));
        assertThat(exclude.getConfigurations(),
                Matchers.equalTo(Collections.singleton(configurationName)));
    }
}
