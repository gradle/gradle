/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.binarycompatibility

import japicmp.model.JApiChangeStatus
import me.champeau.gradle.japicmp.JapicmpTask
import org.gradle.binarycompatibility.filters.AnonymousClassesFilter
import org.gradle.binarycompatibility.filters.KotlinInternalFilter
import org.gradle.binarycompatibility.rules.AcceptedRegressionsRulePostProcess
import org.gradle.binarycompatibility.rules.AcceptedRegressionsRuleSetup
import org.gradle.binarycompatibility.rules.BinaryBreakingChangesRule
import org.gradle.binarycompatibility.rules.IncubatingInternalInterfaceAddedRule
import org.gradle.binarycompatibility.rules.IncubatingMissingRule
import org.gradle.binarycompatibility.rules.KotlinModifiersBreakingChangeRule
import org.gradle.binarycompatibility.rules.MethodsRemovedInInternalSuperClassRule
import org.gradle.binarycompatibility.rules.NewIncubatingAPIRule
import org.gradle.binarycompatibility.rules.NullabilityBreakingChangesRule
import org.gradle.binarycompatibility.rules.SinceAnnotationMissingRule
import org.gradle.binarycompatibility.rules.SinceAnnotationMissingRuleCurrentGradleVersionSetup


class BinaryCompatibilityHelper {

    static setupJApiCmpRichReportRules(
        JapicmpTask japicmpTask,
        AcceptedApiChanges acceptedViolations,
        Set<File> sourceRoots,
        String currentVersion
    ) {
        japicmpTask.tap {

            addExcludeFilter(AnonymousClassesFilter)
            addExcludeFilter(KotlinInternalFilter)

            def acceptedChangesMap = acceptedViolations.toAcceptedChangesMap()

            richReport.tap {
                addRule(IncubatingInternalInterfaceAddedRule, [
                    acceptedApiChanges: acceptedChangesMap,
                    publicApiPatterns: richReport.includedClasses
                ])
                addRule(MethodsRemovedInInternalSuperClassRule, [
                    acceptedApiChanges: acceptedChangesMap,
                    publicApiPatterns: richReport.includedClasses
                ])
                addRule(BinaryBreakingChangesRule, acceptedChangesMap)
                addRule(NullabilityBreakingChangesRule, acceptedChangesMap)
                addRule(KotlinModifiersBreakingChangeRule, acceptedChangesMap)
                addRule(JApiChangeStatus.NEW, IncubatingMissingRule, acceptedChangesMap)
                addRule(JApiChangeStatus.NEW, SinceAnnotationMissingRule, acceptedChangesMap)
                addRule(JApiChangeStatus.NEW, NewIncubatingAPIRule, acceptedChangesMap)

                addSetupRule(AcceptedRegressionsRuleSetup, acceptedChangesMap)
                addSetupRule(SinceAnnotationMissingRuleCurrentGradleVersionSetup, [currentVersion: currentVersion])

                addPostProcessRule(AcceptedRegressionsRulePostProcess)
            }

            doFirst {
                richReport.tap {
                    addSetupRule(BinaryCompatibilityRepositorySetupRule, [
                        (BinaryCompatibilityRepositorySetupRule.Params.sourceRoots): sourceRoots.collect { it.absolutePath } as Set,
                        (BinaryCompatibilityRepositorySetupRule.Params.sourceCompilationClasspath): newClasspath.collect { it.absolutePath } as Set
                    ])
                    addPostProcessRule(BinaryCompatibilityRepositoryPostProcessRule)
                }
            }
        }
    }
}
