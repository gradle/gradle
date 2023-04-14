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

import japicmp.model.JApiChangeStatus
import japicmp.model.JApiClass
import japicmp.model.JApiCompatibility
import javassist.CtClass
import me.champeau.gradle.japicmp.report.Violation

class IncubatingInternalInterfaceAddedRule extends AbstractSuperClassChangesRule {

    IncubatingInternalInterfaceAddedRule(Map<String, Object> params) {
        super(params)
    }

    protected boolean changed(JApiCompatibility member) {
        return member.getChangeStatus() == JApiChangeStatus.MODIFIED
    }

    protected Violation checkSuperClassChanges(JApiClass c, CtClass oldClass, CtClass newClass) {
        Map<String, CtClass> oldInterfaces = collectImplementedInterfaces(oldClass)
        Map<String, CtClass> newInterfaces = collectImplementedInterfaces(newClass)

        newInterfaces.keySet().removeAll(oldInterfaces.keySet())

        if (newInterfaces.isEmpty()) {
            return null
        }

        List<String> changes = filterChangesToReport(newClass, newInterfaces)
        if (changes.isEmpty()) {
            return null
        }
        return acceptOrReject(c, changes, Violation.error(c, " introduces internal or incubating interfaces"))
    }

    private Map<String, CtClass> collectImplementedInterfaces(CtClass c) {
        Map<String, CtClass> result = [:]
        collect(result, c)
        return result
    }

    private void collect(Map<String, CtClass> result, CtClass c) {
        c.interfaces.each { result.put(it.name, it) }

        if (c.superclass != null) {
            collect(result, c.superclass)
        }
    }

    private List<String> filterChangesToReport(CtClass c, Map<String, CtClass> interfaces) {
        return interfaces.values().findAll { implementedDirectly(it, c) && addedInterfaceIsIncubatingOrInternal(it, c) }*.name.sort()
    }

    private boolean implementedDirectly(CtClass interf, CtClass c) {
        return c.interfaces.any { it.name == interf.name }
    }

    private boolean addedInterfaceIsIncubatingOrInternal(CtClass interf, CtClass c) {
        return (isIncubating(interf) && !isIncubating(c)) || isInternal(interf)
    }

    private boolean isIncubating(CtClass c) {
        return c.annotations.any { it.annotationType().name == 'org.gradle.api.Incubating' }
    }
}
