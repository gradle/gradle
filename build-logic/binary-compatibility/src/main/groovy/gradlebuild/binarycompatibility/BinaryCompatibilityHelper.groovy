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

package gradlebuild.binarycompatibility

import gradlebuild.binarycompatibility.filters.AnonymousClassesFilter
import gradlebuild.binarycompatibility.filters.KotlinInternalFilter
import gradlebuild.binarycompatibility.rules.AcceptedRegressionsRulePostProcess
import gradlebuild.binarycompatibility.rules.AcceptedRegressionsRuleSetup
import gradlebuild.binarycompatibility.rules.BinaryBreakingChangesRule
import gradlebuild.binarycompatibility.rules.BinaryBreakingSuperclassChangeRule
import gradlebuild.binarycompatibility.rules.IncubatingInternalInterfaceAddedRule
import gradlebuild.binarycompatibility.rules.IncubatingMissingRule
import gradlebuild.binarycompatibility.rules.KotlinModifiersBreakingChangeRule
import gradlebuild.binarycompatibility.rules.MethodsRemovedInInternalSuperClassRule
import gradlebuild.binarycompatibility.rules.NewIncubatingAPIRule
import gradlebuild.binarycompatibility.rules.NullabilityBreakingChangesRule
import gradlebuild.binarycompatibility.rules.SinceAnnotationMissingRule
import gradlebuild.binarycompatibility.rules.SinceAnnotationMissingRuleCurrentGradleVersionSetup
import japicmp.model.JApiChangeStatus
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection

class BinaryCompatibilityHelper {
    static setupJApiCmpRichReportRules(
        JapicmpTask japicmpTask,
        AcceptedApiChanges acceptedViolations,
        FileCollection sourceRoots,
        String currentVersion,
        File apiChangesJsonFile,
        Directory projectRootDir
    ) {
        japicmpTask.tap {
            addExcludeFilter(AnonymousClassesFilter)
            addExcludeFilter(KotlinInternalFilter)

            def acceptedChangesMap = acceptedViolations.toAcceptedChangesMap()

            def apiChangesJsonFilePath = apiChangesJsonFile.path
            def projectRootDirPath = projectRootDir.asFile.path

            richReport.get().tap {
                addRule(IncubatingInternalInterfaceAddedRule, [
                    acceptedApiChanges: acceptedChangesMap,
                    publicApiPatterns: includedClasses.get(),
                    apiChangesJsonFile: apiChangesJsonFilePath,
                    projectRootDir: projectRootDirPath
                ])
                addRule(MethodsRemovedInInternalSuperClassRule, [
                    acceptedApiChanges: acceptedChangesMap,
                    publicApiPatterns: includedClasses.get(),
                    apiChangesJsonFile: apiChangesJsonFilePath,
                    projectRootDir: projectRootDirPath
                ])
                addRule(BinaryBreakingSuperclassChangeRule, [
                    acceptedApiChanges: acceptedChangesMap,
                    publicApiPatterns: includedClasses.get(),
                    apiChangesJsonFile: apiChangesJsonFilePath,
                    projectRootDir: projectRootDirPath
                ])
                addRule(BinaryBreakingChangesRule, [
                        acceptedApiChanges: acceptedChangesMap,
                        apiChangesJsonFile: apiChangesJsonFilePath,
                        projectRootDir: projectRootDirPath
                ])
                addRule(NullabilityBreakingChangesRule, [
                        acceptedApiChanges: acceptedChangesMap,
                        apiChangesJsonFile: apiChangesJsonFilePath,
                        projectRootDir: projectRootDirPath
                ])
                addRule(KotlinModifiersBreakingChangeRule, [
                        acceptedApiChanges: acceptedChangesMap,
                        apiChangesJsonFile: apiChangesJsonFilePath,
                        projectRootDir: projectRootDirPath
                ])
                addRule(JApiChangeStatus.NEW, IncubatingMissingRule, [
                        acceptedApiChanges: acceptedChangesMap,
                        apiChangesJsonFile: apiChangesJsonFilePath,
                        projectRootDir: projectRootDirPath
                ])
                addRule(JApiChangeStatus.NEW, SinceAnnotationMissingRule, [
                        acceptedApiChanges: acceptedChangesMap,
                        apiChangesJsonFile: apiChangesJsonFilePath,
                        projectRootDir: projectRootDirPath
                ])
                addRule(JApiChangeStatus.NEW, NewIncubatingAPIRule, [
                        acceptedApiChanges: acceptedChangesMap,
                        apiChangesJsonFile: apiChangesJsonFilePath,
                        projectRootDir: projectRootDirPath
                ])

                addSetupRule(AcceptedRegressionsRuleSetup, acceptedChangesMap)
                addSetupRule(SinceAnnotationMissingRuleCurrentGradleVersionSetup, [currentVersion: currentVersion])
                addSetupRule(BinaryCompatibilityRepositorySetupRule, [
                    (BinaryCompatibilityRepositorySetupRule.Params.sourceRoots): sourceRoots.collect { it.absolutePath } as Set,
                    (BinaryCompatibilityRepositorySetupRule.Params.sourceCompilationClasspath): newClasspath.collect { it.absolutePath } as Set
                ])

                addPostProcessRule(AcceptedRegressionsRulePostProcess)
                addPostProcessRule(BinaryCompatibilityRepositoryPostProcessRule)
            }
        }
    }
}
