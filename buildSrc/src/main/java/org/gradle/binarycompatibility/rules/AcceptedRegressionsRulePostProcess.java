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

import java.util.Set;
import java.util.HashSet;
import me.champeau.gradle.japicmp.report.PostProcessViolationsRule;
import me.champeau.gradle.japicmp.report.ViolationCheckContextWithViolations;

public class AcceptedRegressionsRulePostProcess implements PostProcessViolationsRule {
    @SuppressWarnings("unchecked")
    public void execute(ViolationCheckContextWithViolations context) {
        Set<String> declaredRegressions = (Set<String>) context.getUserData().get("declaredRegressions");
        Set<String> seenRegressions = (Set<String>) context.getUserData().get("seenRegressions");
        Set<String> left = new HashSet<String>(declaredRegressions);
        left.removeAll(seenRegressions);
        if (!left.isEmpty()) {
            throw new RuntimeException("The following regressions are declared as accepted, but didn't match any rule " + left);
        }
    }
}
