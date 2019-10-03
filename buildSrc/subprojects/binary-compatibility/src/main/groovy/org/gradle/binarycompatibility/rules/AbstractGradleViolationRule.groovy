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

import com.google.gson.Gson
import com.google.gson.stream.JsonWriter
import groovy.transform.CompileStatic
import japicmp.model.JApiChangeStatus
import japicmp.model.JApiClass
import japicmp.model.JApiCompatibility
import japicmp.model.JApiConstructor
import japicmp.model.JApiField
import japicmp.model.JApiHasAnnotations
import japicmp.model.JApiHasChangeStatus
import japicmp.model.JApiMethod
import me.champeau.gradle.japicmp.report.AbstractContextAwareViolationRule
import me.champeau.gradle.japicmp.report.Violation
import org.gradle.api.Incubating
import org.gradle.binarycompatibility.AcceptedApiChange
import org.gradle.binarycompatibility.AcceptedApiChanges
import org.gradle.binarycompatibility.ApiChange
import org.gradle.binarycompatibility.BinaryCompatibilityRepository
import org.gradle.binarycompatibility.BinaryCompatibilityRepositorySetupRule

import javax.inject.Inject

@CompileStatic
abstract class AbstractGradleViolationRule extends AbstractContextAwareViolationRule {

    private final Map<ApiChange, String> acceptedApiChanges

    AbstractGradleViolationRule(Map<String, String> acceptedApiChanges) {
        this.acceptedApiChanges = AcceptedApiChanges.fromAcceptedChangesMap(acceptedApiChanges)
    }

    protected BinaryCompatibilityRepository getRepository() {
        return context.userData[BinaryCompatibilityRepositorySetupRule.REPOSITORY_CONTEXT_KEY] as BinaryCompatibilityRepository
    }

    protected static boolean isNewOrRemoved(JApiCompatibility member) {
        if (member instanceof JApiHasChangeStatus) {
            return ((JApiHasChangeStatus) member).changeStatus in [
                JApiChangeStatus.NEW,
                JApiChangeStatus.REMOVED
            ]
        }
        return true
    }

    private static boolean isAnnotatedWithIncubating(JApiHasAnnotations member) {
        member.annotations*.fullyQualifiedName.any { it == Incubating.name }
    }

    private static boolean isAnnotatedWithDeprecated(JApiHasAnnotations member) {
        member.annotations*.fullyQualifiedName.any { it == Deprecated.name || it == kotlin.Deprecated.name }
    }

    private static boolean isAnnotatedWithInject(JApiHasAnnotations member) {
        member.annotations*.fullyQualifiedName.any { it == Inject.name }
    }

    protected static boolean isInject(JApiHasAnnotations member) {
        return isAnnotatedWithInject(member)
    }

    protected boolean isIncubating(JApiHasAnnotations member) {
        if (member instanceof JApiClass) {
            return isIncubatingClass((JApiClass) member)
        }
        if (member instanceof JApiMethod) {
            return isIncubatingOrOverrideMethod((JApiMethod) member)
        }
        if (member instanceof JApiField) {
            return isIncubatingField((JApiField) member)
        }
        if (member instanceof JApiConstructor) {
            return isIncubatingConstructor((JApiConstructor) member)
        }
        return isAnnotatedWithIncubating(member)
    }

    private static boolean isIncubatingClass(JApiClass clazz) {
        return isAnnotatedWithIncubating(clazz)
    }

    private boolean isIncubatingOrOverrideMethod(JApiMethod method) {
        return isAnnotatedWithIncubating(method) || isAnnotatedWithIncubating(method.jApiClass) || isOverride(method)
    }

    private static boolean isIncubatingField(JApiField field) {
        return isAnnotatedWithIncubating(field) || isAnnotatedWithIncubating(field.jApiClass)
    }

    private static boolean isIncubatingConstructor(JApiConstructor constructor) {
        return isAnnotatedWithIncubating(constructor) || isAnnotatedWithIncubating(constructor.jApiClass)
    }

    protected boolean isOverride(JApiMethod method) {
        // No point in parsing the source file if the method is not there any more.
        if (method.changeStatus == JApiChangeStatus.REMOVED) {
            return false
        }
        // @Override has source retention - so we need to peek into the sources
        return repository.isOverride(method)
    }

    Violation acceptOrReject(JApiCompatibility member, Violation rejection) {
        List<String> changes = member.compatibilityChanges.collect { Violation.describe(it) }
        return acceptOrReject(member, changes, rejection)
    }

    Violation acceptOrReject(JApiCompatibility member, List<String> changes, Violation rejection) {
        Set<ApiChange> seenApiChanges = (Set<ApiChange>) context.userData["seenApiChanges"]
        def change = new ApiChange(
            context.className,
            Violation.describe(member),
            changes
        )
        String acceptationReason = acceptedApiChanges[change]
        if (acceptationReason != null) {
            seenApiChanges.add(change)
            return Violation.accept(member, "${rejection.getHumanExplanation()}. Reason for accepting this: <b>$acceptationReason</b>")
        }
        def acceptanceJson = new AcceptedApiChange(
            change.type,
            change.member,
            '[ADD YOUR CUSTOM REASON HERE]',
            change.changes
        )

        def id = "accept" + (change.type + change.member).replaceAll('[^a-zA-Z0-9]', '_')
        Violation violation = Violation.error(
            member,
            rejection.getHumanExplanation() + """. If you did this intentionally, please accept the change and provide an explanation:
                <a class="btn btn-info" role="button" data-toggle="collapse" href="#${id}" aria-expanded="false" aria-controls="collapseExample">Accept this change</a>
                <div class="collapse" id="${id}">
                  <div class="well">
                      In order to accept this change add the following to <code>subprojects/distributions/src/changes/accepted-public-api-changes.json</code>:
                    <pre>${prettyPrintJson(acceptanceJson)}</pre>
                  </div>
                </div>""".stripIndent()
        )
        return violation
    }

    private static String prettyPrintJson(def acceptanceJson) {
        def stringWriter = new StringWriter()
        new JsonWriter(stringWriter).withCloseable { writer ->
            writer.setIndent("    ")
            new Gson().toJson(acceptanceJson, AcceptedApiChange, writer)
        }
        return stringWriter.toString()
    }

    String getCurrentVersion() {
        return context.getUserData().get("currentVersion")
    }
}
