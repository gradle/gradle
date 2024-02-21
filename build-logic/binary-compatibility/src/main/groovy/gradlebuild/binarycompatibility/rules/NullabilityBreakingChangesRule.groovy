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

import groovy.transform.CompileStatic
import japicmp.model.JApiCompatibility
import japicmp.model.JApiConstructor
import japicmp.model.JApiField
import japicmp.model.JApiMethod
import javassist.CtBehavior
import javassist.CtField
import javassist.CtMethod
import javassist.Modifier
import javassist.bytecode.annotation.AnnotationImpl
import me.champeau.gradle.japicmp.report.Violation

import javax.annotation.Nullable
import java.lang.reflect.Proxy

@CompileStatic
class NullabilityBreakingChangesRule extends AbstractGradleViolationRule {

    NullabilityBreakingChangesRule(Map<String, Object> params) {
        super(params)
    }

    @Override
    Violation maybeViolation(JApiCompatibility member) {

        if (isNewOrRemoved(member)) {
            return null
        }

        List<String> warnings = []
        List<String> errors = []

        def inspectParametersNullabilityOf = { CtBehavior oldBehavior, CtBehavior newBehavior ->

            List<Boolean> oldParametersNullability = parametersNullabilityOf(oldBehavior)
            List<Boolean> newParametersNullability = parametersNullabilityOf(newBehavior)

            for (int idx = 0; idx < oldParametersNullability.size(); idx++) {
                def oldNullability = oldParametersNullability[idx]
                def newNullability = newParametersNullability[idx]
                if (oldNullability && !newNullability) {
                    errors << "Parameter $idx from null accepting to non-null accepting breaking change".toString()
                } else if (!oldNullability && newNullability) {
                    warnings << "Parameter $idx nullability changed from non-nullable to nullable".toString()
                }
            }
        }

        if (member instanceof JApiField) {

            JApiField field = (JApiField) member
            CtField oldField = field.oldFieldOptional.get()
            CtField newField = field.newFieldOptional.get()

            def oldNullability = oldField.annotations.any { isNullableCtAnnotation(it) }
            def newNullability = newField.annotations.any { isNullableCtAnnotation(it) }

            if (Modifier.isFinal(oldField.modifiers) && Modifier.isFinal(newField.modifiers)) {
                if (!oldNullability && newNullability) {
                    errors << "From non-nullable to nullable breaking change"
                } else if (oldNullability && !newNullability) {
                    warnings << "Nullability changed from nullable to non-nullable"
                }
            } else if (oldNullability != newNullability) {
                errors << "Nullability breaking change"
            }

        } else if (member instanceof JApiConstructor) {

            JApiConstructor ctor = (JApiConstructor) member
            inspectParametersNullabilityOf(ctor.oldConstructor.get(), ctor.newConstructor.get())

        } else if (member instanceof JApiMethod) {

            JApiMethod method = (JApiMethod) member
            CtMethod oldMethod = method.oldMethod.get()
            CtMethod newMethod = method.newMethod.get()

            inspectParametersNullabilityOf(oldMethod, newMethod)

            def oldNullability = oldMethod.annotations.any { isNullableCtAnnotation(it) }
            def newNullability = newMethod.annotations.any { isNullableCtAnnotation(it) }

            if (!oldNullability && newNullability) {
                errors << "From non-null returning to null returning breaking change"
            } else if (oldNullability && !newNullability) {
                warnings << "Return nullability changed from nullable to non-nullable"
            }
        }

        if (!errors.isEmpty()) {
            def changes = errors + warnings
            return acceptOrReject(member, changes, Violation.error(member, changes.join(" ")))
        }
        if (!warnings.isEmpty()) {
            return Violation.warning(member, warnings.join(" "))
        }
        return null
    }

    private static List<Boolean> parametersNullabilityOf(CtBehavior behavior) {
        def annotations = behavior.parameterAnnotations as List<Object[]>
        annotations.collect { Object[] pAnn ->
            pAnn.flatten().any { isNullableCtAnnotation(it) }
        }
    }

    private static boolean isNullableCtAnnotation(Object ann) {
        if (Proxy.isProxyClass(ann.class)) {
            def typeName = (Proxy.getInvocationHandler(ann) as AnnotationImpl).annotation.typeName
            return Nullable.name == typeName || org.jetbrains.annotations.Nullable.name == typeName
        }
        return false
    }
}
