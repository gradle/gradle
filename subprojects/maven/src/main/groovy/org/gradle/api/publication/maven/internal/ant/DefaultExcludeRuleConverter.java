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
package org.gradle.api.publication.maven.internal.ant;

import org.apache.maven.model.Exclusion;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.publication.maven.internal.ExcludeRuleConverter;


public class DefaultExcludeRuleConverter implements ExcludeRuleConverter {
    public Exclusion convert(ExcludeRule excludeRule) {
        if (isConvertable(excludeRule)) {
            Exclusion exclusion = new Exclusion();
            exclusion.setGroupId(excludeRule.getGroup());
            exclusion.setArtifactId(excludeRule.getModule());
            return exclusion;
        }
        return null;
    }

    private boolean isConvertable(ExcludeRule excludeRule) {
        return excludeRule.getGroup()!=null && excludeRule.getModule()!=null;
    }
}
