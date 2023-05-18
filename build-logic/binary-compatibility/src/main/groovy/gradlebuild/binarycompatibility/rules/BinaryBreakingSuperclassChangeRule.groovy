/*
 * Copyright 2022 the original author or authors.
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
import javassist.CtClass
import me.champeau.gradle.japicmp.report.Violation

/**
 * Workaround for <a href="https://github.com/melix/japicmp-gradle-plugin/issues/56">japicmp issue w.r.t. superclass breakage</a>.
 *
 * <p>
 * Reports simple superclass changes (e.g. the removal of a superclass) as a breaking change, as it affects what methods can be
 * called with the given type, even if the methods and fields inherited don't change.
 * </p>
 */
class BinaryBreakingSuperclassChangeRule extends AbstractSuperClassChangesRule {

    BinaryBreakingSuperclassChangeRule(Map<String, Object> params) {
        super(params)
    }

    @Override
    protected boolean changed(JApiCompatibility member) {
        return member instanceof JApiClass && !member.superclass.binaryCompatible && !member.superclass.compatibilityChanges.empty
    }

    @Override
    protected Violation checkSuperClassChanges(JApiClass apiClass, CtClass oldClass, CtClass newClass) {
        return acceptOrReject(apiClass.superclass, Violation.notBinaryCompatible(apiClass.superclass))
    }
}
