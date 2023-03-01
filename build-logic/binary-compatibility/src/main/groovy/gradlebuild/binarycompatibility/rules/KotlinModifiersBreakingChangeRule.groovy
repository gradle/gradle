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

import gradlebuild.binarycompatibility.metadata.KotlinMetadataQueries
import groovy.transform.CompileStatic
import japicmp.model.JApiCompatibility
import japicmp.model.JApiMethod
import me.champeau.gradle.japicmp.report.Violation

@CompileStatic
class KotlinModifiersBreakingChangeRule extends AbstractGradleViolationRule {

    KotlinModifiersBreakingChangeRule(Map<String, Object> params) {
        super(params)
    }

    @Override
    Violation maybeViolation(JApiCompatibility member) {

        if (isNewOrRemoved(member) || !(member instanceof JApiMethod)) {
            return null
        }

        JApiMethod method = (JApiMethod) member

        def metadata = KotlinMetadataQueries.INSTANCE

        def oldMethod = method.oldMethod.get()
        def newMethod = method.newMethod.get()

        def oldIsOperator = metadata.isKotlinOperatorFunction(oldMethod)
        def newIsOperator = metadata.isKotlinOperatorFunction(newMethod)

        def oldIsInfix = metadata.isKotlinInfixFunction(oldMethod)
        def newIsInfix = metadata.isKotlinInfixFunction(newMethod)

        def operatorChanged = oldIsOperator != newIsOperator
        def infixChanged = oldIsInfix != newIsInfix

        if (operatorChanged || infixChanged) {
            List<String> changes = []
            if (operatorChanged) {
                changes.add(modifierChangeDetail("operator", newIsOperator))
            }
            if (infixChanged) {
                changes.add(modifierChangeDetail("infix", newIsInfix))
            }
            return acceptOrReject(member, changes, Violation.error(member, "Breaking Kotlin modifier change"))
        }

        return null
    }

    private static String modifierChangeDetail(String modifier, boolean added) {
        return "$modifier modifier was ${added ? "added" : "removed"}"
    }
}
