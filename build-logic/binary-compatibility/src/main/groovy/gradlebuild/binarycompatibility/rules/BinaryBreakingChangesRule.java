/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.binarycompatibility.rules;

import com.google.common.collect.ImmutableList;
import japicmp.model.JApiClass;
import japicmp.model.JApiCompatibility;
import japicmp.model.JApiCompatibilityChange;
import japicmp.model.JApiConstructor;
import japicmp.model.JApiHasAnnotations;
import japicmp.model.JApiImplementedInterface;
import me.champeau.gradle.japicmp.report.Violation;

import java.util.List;
import java.util.Map;

public class BinaryBreakingChangesRule extends AbstractGradleViolationRule {

    private static final List<JApiCompatibilityChange> IGNORED_CHANGE_TYPES = ImmutableList.of(
        JApiCompatibilityChange.METHOD_REMOVED_IN_SUPERCLASS, // the removal of the method will be reported
        JApiCompatibilityChange.INTERFACE_REMOVED,            // the removed methods will be reported
        JApiCompatibilityChange.INTERFACE_ADDED               // the added methods will be reported
    );

    public BinaryBreakingChangesRule(Map<String, Object> params) {
        super(params);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Violation maybeViolation(final JApiCompatibility member) {
        if (!member.isBinaryCompatible()) {
            if ((member instanceof JApiClass) && (member.getCompatibilityChanges().isEmpty())) {
                // A member of the class breaks binary compatibility.
                // That will be handled when the member is passed to `maybeViolation`.
                return null;
            }
            if (member instanceof JApiImplementedInterface) {
                // The changes about the interface's methods will be reported already
                return null;
            }
            if (member instanceof JApiConstructor) {
                if (isInject((JApiConstructor) member)) {
                    // We do not consider injecting constructors public API
                    return null;
                }
            }
            for (JApiCompatibilityChange change : member.getCompatibilityChanges()) {
                if (IGNORED_CHANGE_TYPES.contains(change)) {
                    return null;
                }
            }
            if (member instanceof JApiHasAnnotations) {
                if (isIncubating((JApiHasAnnotations) member)) {
                    return Violation.warning(member, "Changed public API (@Incubating)");
                }
            }
            return acceptOrReject(member, Violation.notBinaryCompatible(member));
        }
        return null;
    }

}
