/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.architecture.test;

import com.google.common.collect.Sets;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.runner.RunWith;

import java.util.Collection;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packages = { "org.gradle", "org.gradleinternal" })
public class LocaleDependencyTest {

    // TODO replace toLowerCase() calls with TextUtil.toLowerCaseXXX() in the methods below
    private static final Set<String> ignoredMethodsWithToLowerCaseCalls = Sets.newHashSet(
        "org.gradle.util.GUtil.toWords(java.lang.CharSequence, char)",
        "org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler.reportSuppressedDeprecations()",
        "org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin.getConfigurationName()",
        "org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin.getTaskBaseName()",
        "org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin.getReportName()",
        "org.gradle.internal.deprecation.ConfigurationDeprecationType.displayName()",
        "org.gradle.api.internal.provider.AbstractCollectionProperty.describeContents()",
        "org.gradle.internal.os.OperatingSystem.forName(java.lang.String)",
        "org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin$3.execute(org.gradle.api.Task)",
        "org.gradleinternal.buildinit.plugins.internal.maven.Maven2Gradle.convert()",
        "org.gradle.caching.internal.controller.service.BuildCacheServiceRole.<init>(java.lang.String, int)",
        "org.gradle.language.nativeplatform.internal.Dimensions.createDimensionSuffix(java.lang.String, java.util.Collection)",
        "org.gradle.api.tasks.diagnostics.internal.insight.DependencyInsightReporter.createHeaderForDependency(org.gradle.api.tasks.diagnostics.internal.graph.nodes.DependencyEdge, java.util.Set)",
        "org.gradle.util.TextUtil$1.apply(java.lang.String)",
        "org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl.toString()",
        "org.gradle.internal.logging.console.DefaultColorMap.getColorSpecForStyle(org.gradle.internal.logging.text.StyledTextOutput$Style)",
        "org.gradle.internal.logging.console.DefaultColorMap.addDefault(org.gradle.internal.logging.text.StyledTextOutput$Style, [Lorg.gradle.internal.logging.text.StyledTextOutput$Style;)",
        "org.gradle.internal.logging.console.DefaultColorMap.addDefault(org.gradle.internal.logging.text.StyledTextOutput$Style, java.lang.String)",
        "org.gradle.internal.logging.console.DefaultColorMap.getColourFor(org.gradle.internal.logging.text.StyledTextOutput$Style)",
        "org.gradle.internal.os.OperatingSystem$Unix.getOsPrefix()",
        "org.gradle.internal.buildoption.EnumBuildOption.getValue(java.lang.String)",
        "org.gradle.util.internal.GUtil.toWords(java.lang.CharSequence, char)",
        "org.gradle.util.internal.NameMatcher.getKebabCasePatternForName(java.lang.String)",
        "org.gradle.util.internal.TextUtil$1.apply(java.lang.String)",
        "org.gradle.language.base.internal.AbstractLanguageSourceSet.getDisplayName()",
        "org.gradle.api.publish.maven.internal.publisher.MavenRemotePublisher.publish(org.gradle.api.publish.maven.internal.publisher.MavenNormalizedPublication, org.gradle.api.artifacts.repositories.MavenArtifactRepository)",
        "org.gradle.internal.FileUtils.withExtension(java.lang.String, java.lang.String)",
        "org.gradle.plugins.ide.eclipse.model.internal.SourceFoldersCreator.addSourceSetAttribute(org.gradle.api.tasks.SourceSet, org.gradle.plugins.ide.eclipse.model.SourceFolder)",
        "org.gradle.buildinit.plugins.internal.modifiers.Language.withName(java.lang.String)",
        "org.gradle.buildinit.plugins.internal.modifiers.Language.withNameAndExtension(java.lang.String, java.lang.String)",
        "org.gradle.internal.nativeintegration.jansi.DefaultJansiRuntimeResolver.getOperatingSystem()",
        "org.gradle.buildinit.plugins.internal.LanguageSpecificAdaptor.conventionPluginScriptBuilder(java.lang.String, org.gradle.buildinit.plugins.internal.InitSettings)",
        "org.gradle.configurationcache.ConfigurationCacheRepository.stateFile(java.io.File, org.gradle.configurationcache.StateType)",
        "org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder.documented()",
        "org.gradle.internal.nativeintegration.services.NativeServices.initializeNativeIntegrations(java.io.File)",
        "org.gradle.plugins.ear.Ear.recordTopLevelModules(org.gradle.api.file.FileCopyDetails)",
        "org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyXmlModuleDescriptorParser$Parser.extendsStarted(org.xml.sax.Attributes)",
        "org.gradle.internal.jvm.inspection.JvmVendor$KnownJvmVendor.parse(java.lang.String)",
        "org.gradle.internal.jvm.inspection.JvmInstallationMetadata$DefaultJvmInstallationMetadata.determineInstallationType(java.lang.String)",
        "org.gradle.internal.hash.DefaultChecksumService.hash(java.io.File, java.lang.String)",
        "org.gradle.api.internal.catalog.DefaultVersionCatalogBuilder.validateAlias(java.lang.String)",
        "org.gradle.test.fixtures.language.Language.getName()",
        "org.gradle.process.internal.util.MergeOptionsUtil.getHeapSizeMb(java.lang.String)",
        "org.gradle.util.VersionNumber.toLowerCase(java.lang.String)",
        "org.gradle.util.NameMatcher.getKebabCasePatternForName(java.lang.String)",
        "org.gradle.internal.jvm.Jvm.createCurrent()",
        "org.gradle.internal.jvm.Jvm.createCurrent()",
        "org.gradle.cache.internal.DefaultFileLockManager$DefaultFileLock.lock(org.gradle.cache.FileLockManager$LockMode)",
        "org.gradle.nativeplatform.platform.internal.Architectures.forInput(java.lang.String)",
        "org.gradle.api.internal.artifacts.repositories.layout.ResolvedPattern.<init>(java.lang.String, org.gradle.api.internal.file.FileResolver)",
        "org.gradle.api.internal.artifacts.repositories.layout.ResolvedPattern.<init>(java.net.URI, java.lang.String)",
        "org.gradle.api.internal.java.usagecontext.ConfigurationVariantMapping$DefaultConfigurationVariantDetails.assertValidScope(java.lang.String)",
        "org.gradle.api.internal.tasks.properties.ValidationActions.lambda$reportUnexpectedInputKind$8(java.lang.String, java.lang.String, java.io.File, org.gradle.internal.reflect.validation.PropertyProblemBuilder)",
        "org.gradle.api.internal.tasks.properties.ValidationActions.lambda$reportMissingInput$3(java.lang.String, java.lang.String, java.io.File, org.gradle.internal.reflect.validation.PropertyProblemBuilder)",
        "org.gradle.util.internal.VersionNumber.toLowerCase(java.lang.String)",
        "org.gradle.security.internal.SecuritySupport.asciiArmoredFileFor(java.io.File)",
        "org.gradle.plugins.ide.internal.resolver.IdeDependencySet$IdeDependencyResult.isTestConfiguration(java.util.Set)",
        "org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.LoggingExcludeFactory.computeWhatToLog()",
        "org.gradle.api.internal.provider.DefaultMapProperty.describeContents()",
        "org.gradle.api.tasks.diagnostics.internal.AggregateMultiProjectTaskReportModel.isVisible(java.lang.String)",
        "org.gradle.api.tasks.diagnostics.internal.AggregateMultiProjectTaskReportModel.<init>(boolean, boolean, java.lang.String)",
        "org.gradle.api.plugins.quality.TargetJdk.getName()",
        "org.gradle.plugins.ide.idea.model.PathFactory.path(java.lang.String, java.lang.String)",
        "org.gradle.plugins.ide.idea.model.PathFactory.path(java.lang.String, java.lang.String)"
    );

    // TODO replace toUpperCase() calls with TextUtil.toLowerCaseXXX() in the methods below
    private static final Set<String> ignoredMethodsWithToUpperCaseCalls = Sets.newHashSet(
        "org.gradle.internal.resource.transport.aws.s3.S3ConnectionProperties.configureEndpoint(java.lang.String)",
        "org.gradle.internal.resource.transport.aws.s3.S3ConnectionProperties.getProxy()",
        "org.gradle.util.GUtil.toConstant(java.lang.CharSequence)",
        "org.gradle.internal.resource.transport.http.ntlm.NTLMCredentials.determineWorkstationName()",
        "org.gradle.internal.resource.transport.http.ntlm.NTLMCredentials.<init>(org.gradle.api.credentials.PasswordCredentials)",
        "org.gradle.internal.typeconversion.TimeUnitsParser.parseNotation(java.lang.CharSequence, int)",
        "org.gradle.internal.logging.console.DefaultColorMap.createColorFromSpec(java.lang.String)",
        "org.gradle.internal.logging.console.DefaultColorMap.getColourFor(org.gradle.internal.logging.text.Style)",
        "org.gradle.util.internal.GUtil.toConstant(java.lang.CharSequence)",
        "org.gradle.util.internal.NameMatcher.find(java.lang.String, java.util.Collection)",
        "org.gradle.util.internal.NameMatcher.find(java.lang.String, java.util.Collection)",
        "org.gradle.internal.buildevents.BuildResultLogger.buildFinished(org.gradle.BuildResult)",
        "org.gradle.nativeplatform.toolchain.internal.DefaultCommandLineToolInvocationWorker.execute(org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocation, org.gradle.internal.operations.BuildOperationContext)",
        "org.gradle.nativeplatform.toolchain.internal.DefaultCommandLineToolInvocationWorker.execute(org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocation, org.gradle.internal.operations.BuildOperationContext)",
        "org.gradle.internal.logging.LoggingConfigurationBuildOptions$LogLevelOption.parseLogLevel(java.lang.String)",
        "org.gradle.plugins.signing.signatory.pgp.PgpKeyId.normaliseKeyId(java.lang.String)",
        "org.gradle.api.reporting.model.ModelReport.setFormat(java.lang.String)",
        "org.gradle.launcher.daemon.server.api.HandleReportStatus.execute(org.gradle.launcher.daemon.server.api.DaemonCommandExecution)",
        "org.gradle.ide.visualstudio.internal.DefaultVisualStudioProject.getUUID(java.io.File)",
        "org.gradle.internal.resource.transport.gcp.gcs.GcsConnectionProperties.configureEndpoint(java.lang.String)",
        "org.gradle.kotlin.dsl.provider.plugins.precompiled.PrecompiledScriptPluginKt$kebabCaseToCamelCase$1.invoke(kotlin.text.MatchResult)",
        "org.gradle.internal.logging.console.BuildStatusRenderer.phaseStarted(org.gradle.internal.logging.events.ProgressStartEvent, org.gradle.internal.logging.console.BuildStatusRenderer$Phase)",
        "org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver.publishChecksum(org.gradle.internal.resource.ExternalResourceName, java.io.File, java.lang.String, int)",
        "org.gradle.util.NameMatcher.find(java.lang.String, java.util.Collection)",
        "org.gradle.util.NameMatcher.find(java.lang.String, java.util.Collection)",
        "org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.writer.WriteDependencyVerificationFile.writeAsciiArmoredKeyRingFile(java.io.File, com.google.common.collect.ImmutableList)",
        "org.gradle.internal.nativeintegration.filesystem.services.GenericFileSystem.probeCaseSensitive(java.io.File, java.lang.String)",
        "org.gradle.internal.buildoption.EnumBuildOption.getValue(java.lang.String)"
        );

    @ArchTest
    static final ArchRule toLowerCase_not_called_without_locale = noClasses()
        .should(call("java.lang.String.toLowerCase()", "org.gradle.util.internal.TextUtil.toLowerCaseUserLocale(java.lang.String)", ignoredMethodsWithToLowerCaseCalls))
        .as("toLowerCase() must be called with a locale parameter or TextUtil.toLowerCaseUserLocale() should be used");

    @ArchTest
    static final ArchRule toUpperCase_not_called_without_locale = noClasses()
        .should(call("java.lang.String.toUpperCase()", "org.gradle.util.internal.TextUtil.toUpperCaseUserLocale(java.lang.String)", ignoredMethodsWithToUpperCaseCalls))
        .as("toUpperCase() must be called with a locale parameter or TextUtil.toUpperCaseUserLocale() should be used");

    private static ArchCondition<JavaClass> call(String name, String replacement, Collection<String> ignoredMethods) {
        return new ArchCondition<JavaClass>(name + " must be called with a locale parameter or " + replacement + " should be used") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
                    if (name.equals(call.getTarget().getFullName())) {
                        if (!call.getOrigin().getFullName().equals(replacement) && !ignoredMethods.contains(call.getOrigin().getFullName())) {
                            events.add(new SimpleConditionEvent(call, true, call.getDescription()));
                        }
                    }
                }
            }
        };
    }
}
