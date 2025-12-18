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
import gradlebuild.binarycompatibility.filters.BridgeForBytecodeUpgradeAdapterClassFilter
import gradlebuild.binarycompatibility.filters.KotlinInvokeDefaultBridgeFilter
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
import gradlebuild.binarycompatibility.rules.SinceAnnotationRule
import gradlebuild.binarycompatibility.rules.SinceAnnotationRuleCurrentGradleVersionSetup
import gradlebuild.binarycompatibility.rules.UpgradePropertiesRulePostProcess
import gradlebuild.binarycompatibility.rules.UpgradePropertiesRuleSetup
import japicmp.model.JApiChangeStatus
import me.champeau.gradle.japicmp.report.RichReport
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider

class BinaryCompatibilityHelper {
    static setupJApiCmpRichReportRules(
        JapicmpTask japicmpTask,
        Project project,
        Provider<AcceptedApiChanges> acceptedViolations,
        FileCollection sourceRoots,
        String currentVersion,
        File mainApiChangesJsonFile,
        Directory projectRootDir,
        File currentUpgradedPropertiesFile,
        File baselineUpgradedPropertiesFile,
        Action<RichReport> configureReport
    ) {
        japicmpTask.tap {
            addExcludeFilter(AnonymousClassesFilter)
            addExcludeFilter(KotlinInternalFilter)
            addExcludeFilter(KotlinInvokeDefaultBridgeFilter)
            addExcludeFilter(BridgeForBytecodeUpgradeAdapterClassFilter)

            def mainApiChangesJsonFilePath = mainApiChangesJsonFile.path
            def projectRootDirPath = projectRootDir.asFile.path

            richReport = project.provider {
                RichReport richReport = project.objects.newInstance(RichReport.class, new Object[0]);
                richReport.getDestinationDir().convention(project.layout.buildDirectory.dir("reports"));
                configureReport.execute(richReport)
                richReport.tap {
                    def acceptedChangesMap = acceptedViolations.get().toAcceptedChangesMap()
                    addRule(IncubatingInternalInterfaceAddedRule, [
                        acceptedApiChanges: acceptedChangesMap,
                        publicApiPatterns: includedClasses.get(),
                        mainApiChangesJsonFile: mainApiChangesJsonFilePath,
                        projectRootDir: projectRootDirPath
                    ])
                    addRule(MethodsRemovedInInternalSuperClassRule, [
                        acceptedApiChanges: acceptedChangesMap,
                        publicApiPatterns: includedClasses.get(),
                        mainApiChangesJsonFile: mainApiChangesJsonFilePath,
                        projectRootDir: projectRootDirPath
                    ])
                    addRule(BinaryBreakingSuperclassChangeRule, [
                        acceptedApiChanges: acceptedChangesMap,
                        publicApiPatterns: includedClasses.get(),
                        mainApiChangesJsonFile: mainApiChangesJsonFilePath,
                        projectRootDir: projectRootDirPath
                    ])
                    addRule(BinaryBreakingChangesRule, [
                        acceptedApiChanges: acceptedChangesMap,
                        mainApiChangesJsonFile: mainApiChangesJsonFilePath,
                        projectRootDir: projectRootDirPath
                    ])
                    addRule(NullabilityBreakingChangesRule, [
                        acceptedApiChanges: acceptedChangesMap,
                        mainApiChangesJsonFile: mainApiChangesJsonFilePath,
                        projectRootDir: projectRootDirPath
                    ])
                    addRule(KotlinModifiersBreakingChangeRule, [
                        acceptedApiChanges: acceptedChangesMap,
                        mainApiChangesJsonFile: mainApiChangesJsonFilePath,
                        projectRootDir: projectRootDirPath
                    ])
                    addRule(JApiChangeStatus.NEW, IncubatingMissingRule, [
                        acceptedApiChanges: acceptedChangesMap,
                        mainApiChangesJsonFile: mainApiChangesJsonFilePath,
                        projectRootDir: projectRootDirPath
                    ])
                    addRule(JApiChangeStatus.NEW, SinceAnnotationRule, [
                        acceptedApiChanges: acceptedChangesMap,
                        mainApiChangesJsonFile: mainApiChangesJsonFilePath,
                        projectRootDir: projectRootDirPath
                    ])
                    addRule(JApiChangeStatus.NEW, NewIncubatingAPIRule, [
                        acceptedApiChanges: acceptedChangesMap,
                        mainApiChangesJsonFile: mainApiChangesJsonFilePath,
                        projectRootDir: projectRootDirPath
                    ])

                    addSetupRule(AcceptedRegressionsRuleSetup, acceptedChangesMap)
                    addSetupRule(SinceAnnotationRuleCurrentGradleVersionSetup, [currentVersion: currentVersion])
                    addSetupRule(BinaryCompatibilityRepositorySetupRule, [
                        (BinaryCompatibilityRepositorySetupRule.Params.sourceRoots): sourceRoots.collect { it.absolutePath } as Set,
                        (BinaryCompatibilityRepositorySetupRule.Params.sourceCompilationClasspath): newClasspath.collect { it.absolutePath } as Set
                    ])
                    addSetupRule(UpgradePropertiesRuleSetup, [
                        currentUpgradedProperties: currentUpgradedPropertiesFile.absolutePath,
                        baselineUpgradedProperties: baselineUpgradedPropertiesFile.absolutePath
                    ])

                    addPostProcessRule(AcceptedRegressionsRulePostProcess)
                    addPostProcessRule(BinaryCompatibilityRepositoryPostProcessRule)
                    addPostProcessRule(UpgradePropertiesRulePostProcess)
                }
            }
        }
    }
}
