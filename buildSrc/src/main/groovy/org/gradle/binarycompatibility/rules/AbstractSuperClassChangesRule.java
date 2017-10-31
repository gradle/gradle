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

import me.champeau.gradle.japicmp.report.Violation;
import japicmp.model.JApiClass;
import japicmp.model.JApiCompatibility;
import javassist.CtClass;
import com.google.common.base.Optional;

import java.util.Map;

public abstract class AbstractSuperClassChangesRule extends AbstractGradleViolationRule {

    public AbstractSuperClassChangesRule(Map<String, String> acceptedApiChanges) {
        super(acceptedApiChanges);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Violation maybeViolation(final JApiCompatibility member) {
        if (!(member instanceof JApiClass)) {
            return null;
        }

        if (!changed(member)) {
            return null;
        }

        Optional<CtClass> oldClass = ((JApiClass) member).getOldClass();
        Optional<CtClass> newClass = ((JApiClass) member).getNewClass();
        if (!oldClass.isPresent() || !newClass.isPresent()) {
            // breaking change would be reported
            return null;
        }

        try {
            return checkSuperClassChanges((JApiClass) member, oldClass.get(), newClass.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract boolean changed(JApiCompatibility member);

    protected abstract Violation checkSuperClassChanges(JApiClass apiClass, CtClass oldClass, CtClass newClass) throws Exception;

    protected boolean isInternal(CtClass c) {
        return c.getName().contains(".internal.");
    }

}
