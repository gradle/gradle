/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.binarycompatibility.rules;

import me.champeau.gradle.japicmp.report.PostProcessViolationsRule;
import me.champeau.gradle.japicmp.report.ViolationCheckContextWithViolations;
import org.gradle.binarycompatibility.ApiChange;
import org.gradle.util.CollectionUtils;

import java.util.HashSet;
import java.util.Set;

public class AcceptedRegressionsRulePostProcess implements PostProcessViolationsRule {

    @Override
    @SuppressWarnings("unchecked")
    public void execute(ViolationCheckContextWithViolations context) {
        Set<ApiChange> acceptedApiChanges = (Set<ApiChange>) context.getUserData().get("acceptedApiChanges");
        Set<ApiChange> seenApiChanges = (Set<ApiChange>) context.getUserData().get("seenApiChanges");
        Set<ApiChange> left = new HashSet<>(acceptedApiChanges);
        left.removeAll(seenApiChanges);
        if (!left.isEmpty()) {
            String formattedLeft = CollectionUtils.join("\n", left);
            throw new RuntimeException("The following regressions are declared as accepted, but didn't match any rule:\n\n" + formattedLeft);
        }
    }

}
