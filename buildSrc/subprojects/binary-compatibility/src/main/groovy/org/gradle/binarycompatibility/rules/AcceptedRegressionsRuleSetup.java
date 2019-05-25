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

import me.champeau.gradle.japicmp.report.SetupRule;
import me.champeau.gradle.japicmp.report.ViolationCheckContext;
import org.gradle.binarycompatibility.AcceptedApiChanges;
import org.gradle.binarycompatibility.ApiChange;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AcceptedRegressionsRuleSetup implements SetupRule {

    private final Set<ApiChange> acceptedApiChanges;

    public AcceptedRegressionsRuleSetup(Map<String, String> acceptedApiChanges) {
        this.acceptedApiChanges = AcceptedApiChanges.fromAcceptedChangesMap(acceptedApiChanges).keySet();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(ViolationCheckContext context) {
        Map<String, Object> userData = (Map<String, Object>) context.getUserData();
        userData.put("acceptedApiChanges", acceptedApiChanges);
        userData.put("seenApiChanges", new HashSet<ApiChange>());
    }

}
