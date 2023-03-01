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

package gradlebuild.binarycompatibility.rules

import japicmp.model.JApiClass
import japicmp.model.JApiCompatibility
import japicmp.util.Optional
import javassist.CtClass
import me.champeau.gradle.japicmp.report.Violation

import java.util.regex.Pattern

abstract class AbstractSuperClassChangesRule extends AbstractGradleViolationRule {

    private final List<Pattern> publicApiPatterns

    AbstractSuperClassChangesRule(Map<String, Object> params) {
        super(params)
        final List<String> publicApiPatterns = (List<String>)params['publicApiPatterns'];
        this.publicApiPatterns = publicApiPatterns.collect { Pattern.compile(it) }
    }

    Violation maybeViolation(final JApiCompatibility member) {
        if (!(member instanceof JApiClass)) {
            return null
        }

        if (!changed(member)) {
            return null
        }

        Optional<CtClass> oldClass = member.oldClass
        Optional<CtClass> newClass = member.newClass
        if (!oldClass.isPresent() || !newClass.isPresent()) {
            // breaking change would be reported
            return null
        }

        return checkSuperClassChanges(member, oldClass.get(), newClass.get())
    }

    protected abstract boolean changed(JApiCompatibility member)

    protected abstract Violation checkSuperClassChanges(JApiClass apiClass, CtClass oldClass, CtClass newClass)

    protected boolean isInternal(CtClass c) {
        if (c.name.startsWith("java.")) {
            return false
        } else if (c.name.contains('.internal.')) {
            return true
        } else {
            return !publicApiPatterns.any { it.matcher(c.name).find() }
        }
    }
}
