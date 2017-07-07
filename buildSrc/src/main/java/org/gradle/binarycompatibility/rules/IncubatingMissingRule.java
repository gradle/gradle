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

public class IncubatingMissingRule extends WithIncubatingCheck {

    @Override
    public Violation maybeViolation(final JApiCompatibility member) {
        if (member instanceof JApiMethod) {
            JApiMethod method = (JApiMethod) member;
            if (!isIncubating(method) && !isIncubating(method.getjApiClass())) {
                return Violation.error(member, "New method is not annotated with @Incubating");
            }
        }
        return null;
    }

}
