/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.dependencies.ivy2Maven.dependencies;

import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.apache.ivy.plugins.matcher.ExactPatternMatcher;
import org.apache.ivy.plugins.matcher.PatternMatcher;

/**
 * @author Hans Dockter
 */
public class DefaultExcludeRuleConverter implements ExcludeRuleConverter {
    public DefaultMavenExclude convert(ExcludeRule excludeRule) {
        if (isConvertable(excludeRule)) {
            return new DefaultMavenExclude(excludeRule.getId().getModuleId().getOrganisation(),
                    excludeRule.getId().getModuleId().getName());
        }
        return null;
    }

    private boolean isConvertable(ExcludeRule excludeRule) {
        if (excludeRule.getMatcher() != ExactPatternMatcher.INSTANCE) {
            return false;
        }
        String any = PatternMatcher.ANY_EXPRESSION;
        String org = excludeRule.getId().getModuleId().getOrganisation();
        String name = excludeRule.getId().getModuleId().getName();
        if (org == null || name == null) {
            return false;
        }
        if (org.equals(any) || name.equals(any)) {
            return false;
        }
        return true;
    }
}
