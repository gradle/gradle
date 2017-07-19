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

package org.gradle.binarycompatibility.rules

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import japicmp.model.JApiClass
import japicmp.model.JApiCompatibility
import japicmp.model.JApiHasAnnotations
import japicmp.model.JApiMethod
import me.champeau.gradle.japicmp.report.AbstractContextAwareViolationRule
import me.champeau.gradle.japicmp.report.Violation
import org.gradle.binarycompatibility.AcceptedApiChanges
import org.gradle.binarycompatibility.ApiChange

@CompileStatic
abstract class AbstractGradleViolationRule extends AbstractContextAwareViolationRule {

    private final Map<ApiChange, String> acceptedApiChanges

    AbstractGradleViolationRule(Map<String, String> acceptedApiChanges) {
        this.acceptedApiChanges = AcceptedApiChanges.fromAcceptedChangesMap(acceptedApiChanges)
    }

    private static boolean isAnnotatedWithIncubating(JApiHasAnnotations member) {
        member.annotations*.fullyQualifiedName.any { it == 'org.gradle.api.Incubating' }
    }

    static boolean isIncubating(JApiHasAnnotations member) {
        if (member instanceof JApiClass) {
            return isIncubating((JApiClass) member)
        } else if (member instanceof JApiMethod) {
            return isIncubating((JApiMethod) member)
        }
        return isAnnotatedWithIncubating(member)
    }

    static boolean isIncubating(JApiClass clazz) {
        if (isAnnotatedWithIncubating(clazz)) {
            return true
        }
        // all the methods need to be incubating
        List<JApiMethod> methods = clazz.methods
        if (methods.empty) {
            return false
        }
        for (JApiMethod method : methods) {
            if (!isIncubating(method)) {
                return false
            }
        }
        return true
    }

    static boolean isIncubating(JApiMethod method) {
        return isAnnotatedWithIncubating(method) || isAnnotatedWithIncubating(method.jApiClass)
    }


    Violation acceptOrReject(JApiCompatibility member, Violation rejection) {
        Set<ApiChange> seenApiChanges = (Set<ApiChange>) context.userData["seenApiChanges"]
        List<String> changes = member.compatibilityChanges.collect { Violation.describe(it) }
        def change = new ApiChange(
            context.className,
            Violation.describe(member),
            changes
        )
        String acceptationReason = acceptedApiChanges[change]
        if (acceptationReason != null) {
            seenApiChanges.add(change)
            return Violation.accept(member, acceptationReason)
        }
        def acceptanceJson = new LinkedHashMap<String, Object>([
            type: change.type,
            member: change.member,
            acceptation: '&lt;ADD YOUR CUSTOM REASON HERE&gt;'
        ])
        if (change.changes) {
            acceptanceJson.changes = change.changes
        }

        def id = "accept" + (change.type + change.member).replaceAll('[^a-zA-Z0-9]', '_')
        Violation violation = Violation.error(
            member,
            rejection.getHumanExplanation() + """
                <a class="btn btn-info" role="button" data-toggle="collapse" href="#${id}" aria-expanded="false" aria-controls="collapseExample">Accept this change</a>
                <div class="collapse" id="${id}">
                  <div class="well">
                      In order to accept this change add the following to <code>subprojects/distributions/src/changes/accepted-public-api-changes.json</code>:
                    <pre>${JsonOutput.prettyPrint(JsonOutput.toJson(acceptanceJson))}</pre>
                  </div>
                </div>""".stripIndent()
        )
        return violation
    }

}
