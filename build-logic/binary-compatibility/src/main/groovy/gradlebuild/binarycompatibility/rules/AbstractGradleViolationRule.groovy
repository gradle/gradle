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

import com.google.gson.Gson
import com.google.gson.stream.JsonWriter
import gradlebuild.binarycompatibility.AcceptedApiChange
import gradlebuild.binarycompatibility.AcceptedApiChanges
import gradlebuild.binarycompatibility.ApiChange
import gradlebuild.binarycompatibility.BinaryCompatibilityRepository
import gradlebuild.binarycompatibility.BinaryCompatibilityRepositorySetupRule
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

import javax.inject.Inject

@CompileStatic
abstract class AbstractGradleViolationRule extends AbstractContextAwareViolationRule {

    private final Map<ApiChange, String> acceptedApiChanges
    private final File apiChangesJsonFile
    private final File projectRootDir

    AbstractGradleViolationRule(Map<String, Object> params) {
        Map<String, String> acceptedApiChanges = (Map<String, String>)params.get("acceptedApiChanges")
        this.acceptedApiChanges = acceptedApiChanges ? AcceptedApiChanges.fromAcceptedChangesMap(acceptedApiChanges) : [:]

        // Tests will not supply these
        this.apiChangesJsonFile = params.get("apiChangesJsonFile") ? new File(params.get("apiChangesJsonFile") as String) : null
        this.projectRootDir = params.get("projectRootDir") ? new File(params.get("projectRootDir") as String) : null
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

        def changeId = (change.type + change.member).replaceAll('[^a-zA-Z0-9]', '_')
        Violation violation = Violation.error(
            member,
            rejection.getHumanExplanation() + """.
                <br>
                <p>
                If you did this intentionally, please accept the change and provide an explanation:
                <a class="btn btn-info" role="button" data-toggle="collapse" href="#accept-${changeId}" aria-expanded="false" aria-controls="collapseExample">Accept this change</a>
                <div class="collapse" id="accept-${changeId}">
                  <div class="well">
                      In order to accept this change add the following to <code>${relativePathToApiChanges()}</code>:
                    <pre>${prettyPrintJson(acceptanceJson)}</pre>
                  </div>
                </div>
                </p>
                <p>
                If change was made on the `release` branch but hasn't yet been published to the baseline version, update the baseline version:
                <a class="btn btn-info" role="button" data-toggle="collapse" href="#update-baseline-${changeId}" aria-expanded="false" aria-controls="collapseExample">Update baseline</a>
                <div class="collapse" id="update-baseline-${changeId}">
                  <div class="well">
                      Sometimes, the change was made on the `release` branch but hasn't yet been published to the baseline version.
                      In that case, you can publish a new snapshot from the release branch. This will update `released-versions.json` on `master`.
                      See <a href="https://docs.google.com/document/d/1KA5yI4HL18qOeXjXLTMMD_upkDbNUzTDGNfBGYdQlYw/edit#heading=h.9yqcmqviz47z">the documentation</a> for more details.
                  </div>
                </div>
                </p>
                <p>
                If change was made on the `release` branch but hasn't yet been merged to `master`, merge `release` to `master`:
                <a class="btn btn-info" role="button" data-toggle="collapse" href="#merge-release-${changeId}" aria-expanded="false" aria-controls="collapseExample">Merge release to master</a>
                <div class="collapse" id="merge-release-${changeId}">
                  <div class="well">
                      Merging `release` back to `master` is a regular operation you’re free to do, at any time. Usually, you will see conflicts in `notes.md` or `accepted-public-api-changes.json`.
                      On `master` branch, these two files are usually reset (cleaned up), unless you have special reasons not to do so.
                  </div>
                </div>
                </p>
                """.stripIndent()
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

    private String relativePathToApiChanges() {
        if (null != apiChangesJsonFile && null != projectRootDir) {
            return projectRootDir.relativePath(apiChangesJsonFile)
        } else {
            return "<PATHS TO API CHANGES JSON NOT PROVIDED>"
        }
    }
}
