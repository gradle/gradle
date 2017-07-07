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

import japicmp.model.*;
import me.champeau.gradle.japicmp.report.Violation;
import java.util.Map;
import java.util.Set;

public class AcceptedRegressionRule extends WithIncubatingCheck {
    private final Map<String, String> acceptedViolations;

    public AcceptedRegressionRule(Map<String, String> params) {
        acceptedViolations = params;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Violation maybeViolation(final JApiCompatibility member) {
        if (!member.isBinaryCompatible()) {
            Set<String> seenRegressions = (Set<String>) getContext().getUserData().get("seenRegressions");
            String describe = Violation.describe(member);
            String acceptation = acceptedViolations.get(describe);
            if (acceptation == null) {
                for (String key: acceptedViolations.keySet()) {
                    if (describe.startsWith(key)) {
                        acceptation = acceptedViolations.get(key);
                        seenRegressions.add(key);
                    }
                }
                if (acceptation == null) {
                    if (member instanceof JApiHasAnnotations) {
                        if (isIncubating((JApiHasAnnotations)member)) {
                            return Violation.accept(member, "Removed member was incubating");
                        }
                    }
                }
            } else {
                seenRegressions.add(describe);
            }
            if (acceptation != null) {
                return Violation.accept(member, acceptation);
            } else {
                return Violation.notBinaryCompatible(member);
            }
        }
        return null;
    }
}
