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
import japicmp.model.JApiCompatibilityChange
import javassist.CtClass
import javassist.CtMethod
import javassist.Modifier
import me.champeau.gradle.japicmp.report.Violation

class MethodsRemovedInInternalSuperClassRule extends AbstractSuperClassChangesRule {

    MethodsRemovedInInternalSuperClassRule(Map<String, Object> params) {
        super(params)
    }

    protected boolean changed(JApiCompatibility member) {
        return member.compatibilityChanges.contains(JApiCompatibilityChange.METHOD_REMOVED_IN_SUPERCLASS)
    }

    protected Violation checkSuperClassChanges(JApiClass c, CtClass oldClass, CtClass newClass) {
        Set<CtMethod> oldMethods = collectAllPublicApiMethods(oldClass.superclass)
        Set<CtMethod> newMethods = collectAllPublicApiMethods(newClass.superclass)

        oldMethods.removeAll(newMethods)

        if (oldMethods.isEmpty()) {
            return null
        }

        List<String> changes = filterChangesToReport(oldClass, oldMethods)
        if (changes.isEmpty()) {
            return null
        }
        return acceptOrReject(c, changes, Violation.error(c, " methods removed in internal super class"))
    }

    private Set<CtMethod> collectAllPublicApiMethods(CtClass c) {
        Set<CtMethod> result = [] as Set
        collect(result, c)
        return result
    }

    private void collect(Set<CtMethod> result, CtClass c) {
        if (c == null) {
            return
        }

        result.addAll(c.declaredMethods.findAll { isPublicApi(it) })

        collect(result, c.superclass)
    }

    private boolean isPublicApi(CtMethod method) {
        return Modifier.isPublic(method.modifiers) || Modifier.isProtected(method.modifiers)
    }

    private List<String> filterChangesToReport(CtClass c, Set<CtMethod> methods) {
        return methods.findAll { isFirstPublicClassInHierarchy(it, c) }*.longName.sort()
    }

    private boolean isFirstPublicClassInHierarchy(CtMethod method, CtClass c) {
        List<CtClass> classesContainingMethod = []

        CtClass current = c
        while (current != null) {
            if (containsMethod(current, method)) {
                classesContainingMethod.add(current)
            } else {
                break
            }
            current = current.getSuperclass()
        }

        for (int i = classesContainingMethod.size() - 1; i > 0; --i) {
            current = classesContainingMethod.get(i)
            if (!isInternal(current)) {
                // there's another public super class which contains target method
                // it would be reported somewhere else
                return false
            }
        }

        // I'm the top public class which contains target method
        return true
    }

    private boolean containsMethod(CtClass c, CtMethod method) {
        // TODO signature contains return type
        // but return type can be overridden
        return collectAllPublicApiMethods(c).any { it.name == method.name && it.signature == method.signature }
    }
}
