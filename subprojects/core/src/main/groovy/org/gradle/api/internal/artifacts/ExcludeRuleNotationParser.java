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

package org.gradle.api.internal.artifacts;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.internal.typeconversion.MapKey;
import org.gradle.internal.typeconversion.MapNotationParser;
import org.gradle.api.tasks.Optional;

import java.util.Collection;

public class ExcludeRuleNotationParser extends MapNotationParser<ExcludeRule> {

    @Override
    public void describe(Collection<String> candidateFormats) {
        candidateFormats.add("Maps, e.g. [group: 'org.gradle', module:'gradle-core'].");
    }

    protected ExcludeRule parseMap(@MapKey(ExcludeRule.GROUP_KEY) @Optional String group,
                         @MapKey(ExcludeRule.MODULE_KEY) @Optional String module) {
        if (group == null && module == null) {
            throw new InvalidUserDataException("Either a group or module must be specified. For example: [group:'org.gradle']");
        }
        DefaultExcludeRule excluderule = new DefaultExcludeRule();
        excluderule.setGroup(group);
        excluderule.setModule(module);
        return excluderule;
    }
}
