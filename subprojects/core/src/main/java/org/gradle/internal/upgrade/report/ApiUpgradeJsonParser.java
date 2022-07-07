/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.upgrade.report;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import groovy.json.JsonSlurper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

class ApiUpgradeJsonParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiUpgradeJsonParser.class);

    private static final Pattern METHOD_PATTERN = Pattern.compile("Method (\\w+(?:\\.\\w+)*) (\\w+(?:\\.\\w+)*)\\.(\\w+)\\((.*)\\)");
    private static final Pattern COMMA_LIST_PATTERN = Pattern.compile(",\\s*");

    private static final Map<String, Class<?>> PRIMITIVE_CLASSES = Stream.<Class<?>>of(
        boolean.class,
        byte.class,
        short.class,
        int.class,
        long.class,
        float.class,
        double.class,
        char.class
    ).collect(toMap(Class::getName, identity()));

    private static final Map<String, List<String>> ALL_KNOWN_SUBTYPES = ImmutableMap.<String, List<String>>builder()
        .put("org.gradle.api.Project", ImmutableList.of(
            "org.gradle.api.internal.project.ProjectInternal",
            "org.gradle.kotlin.dsl.support.delegates.ProjectDelegate",
            "org.gradle.api.internal.project.DefaultProject",
            "org.gradle.configurationcache.ProblemReportingCrossProjectModelAccess$ProblemReportingProject",
            "org.gradle.kotlin.dsl.KotlinBuildScript",
            "org.gradle.kotlin.dsl.precompile.PrecompiledProjectScript"
        )).put("org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository", ImmutableList.of(
            "org.gradle.api.internal.artifacts.repositories.DefaultFlatDirArtifactRepository"
        )).put("org.gradle.api.artifacts.repositories.IvyArtifactRepository", ImmutableList.of(
            "org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository"
        )).put("org.gradle.api.artifacts.repositories.IvyArtifactRepositoryMetaDataProvider", ImmutableList.of(
            "org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository$MetaDataProvider"
        )).put("org.gradle.api.artifacts.repositories.MavenArtifactRepository", ImmutableList.of(
            "org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository",
            "org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository"
        )).put("org.gradle.api.artifacts.repositories.UrlArtifactRepository", ImmutableList.of(
            "org.gradle.api.artifacts.repositories.MavenArtifactRepository",
            "org.gradle.api.internal.artifacts.repositories.DefaultUrlArtifactRepository",
            "org.gradle.api.artifacts.repositories.IvyArtifactRepository",
            "org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository",
            "org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository",
            "org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository"
        )).put("org.gradle.api.file.ConfigurableFileTree", ImmutableList.of(
            "org.gradle.api.internal.file.collections.DefaultConfigurableFileTree"
        )).put("org.gradle.api.file.CopyProcessingSpec", ImmutableList.of(
            "org.gradle.api.file.CopySpec",
            "org.gradle.api.file.SyncSpec",
            "org.gradle.api.tasks.AbstractCopyTask",
            "org.gradle.api.internal.file.copy.CopySpecWrapper",
            "org.gradle.api.internal.file.copy.CopySpecInternal",
            "org.gradle.api.tasks.Sync",
            "org.gradle.api.tasks.bundling.AbstractArchiveTask",
            "org.gradle.api.tasks.Copy",
            "org.gradle.api.internal.file.copy.DefaultCopySpec",
            "org.gradle.api.internal.file.copy.DelegatingCopySpecInternal",
            "org.gradle.api.tasks.bundling.Tar",
            "org.gradle.api.tasks.bundling.Zip",
            "org.gradle.language.jvm.tasks.ProcessResources",
            "org.gradle.api.internal.file.copy.SingleParentCopySpec",
            "org.gradle.api.internal.file.copy.DestinationRootCopySpec",
            "org.gradle.jvm.tasks.Jar",
            "org.gradle.api.tasks.bundling.Jar",
            "org.gradle.api.tasks.bundling.War",
            "org.gradle.plugins.ear.Ear"
        )).put("org.gradle.api.file.CopySpec", ImmutableList.of(
            "org.gradle.api.file.SyncSpec",
            "org.gradle.api.tasks.AbstractCopyTask",
            "org.gradle.api.internal.file.copy.CopySpecWrapper",
            "org.gradle.api.internal.file.copy.CopySpecInternal",
            "org.gradle.api.tasks.Sync",
            "org.gradle.api.tasks.bundling.AbstractArchiveTask",
            "org.gradle.api.tasks.Copy",
            "org.gradle.api.internal.file.copy.DefaultCopySpec",
            "org.gradle.api.internal.file.copy.DelegatingCopySpecInternal",
            "org.gradle.api.tasks.bundling.Tar",
            "org.gradle.api.tasks.bundling.Zip",
            "org.gradle.language.jvm.tasks.ProcessResources",
            "org.gradle.api.internal.file.copy.SingleParentCopySpec",
            "org.gradle.api.internal.file.copy.DestinationRootCopySpec",
            "org.gradle.jvm.tasks.Jar",
            "org.gradle.api.tasks.bundling.Jar",
            "org.gradle.api.tasks.bundling.War",
            "org.gradle.plugins.ear.Ear"
        )).put("org.gradle.api.file.DeleteSpec", ImmutableList.of(
            "org.gradle.api.tasks.Delete",
            "org.gradle.api.internal.file.delete.DeleteSpecInternal",
            "org.gradle.api.internal.file.delete.DefaultDeleteSpec"
        )).put("org.gradle.api.file.SourceDirectorySet", ImmutableList.of(
            "org.gradle.api.plugins.antlr.AntlrSourceDirectorySet",
            "org.gradle.api.tasks.GroovySourceDirectorySet",
            "org.gradle.api.internal.file.DefaultSourceDirectorySet",
            "org.gradle.api.tasks.ScalaSourceDirectorySet",
            "org.gradle.api.plugins.antlr.internal.DefaultAntlrSourceDirectorySet",
            "org.gradle.api.internal.tasks.DefaultGroovySourceDirectorySet",
            "org.gradle.api.internal.tasks.DefaultScalaSourceDirectorySet"
        )).put("org.gradle.api.plugins.JavaApplication", ImmutableList.of(
            "org.gradle.api.plugins.internal.DefaultJavaApplication"
        )).put("org.gradle.api.plugins.JavaPluginExtension", ImmutableList.of(
            "org.gradle.api.plugins.internal.DefaultJavaPluginExtension"
        )).put("org.gradle.api.tasks.SourceTask", ImmutableList.of(
            "org.gradle.api.plugins.quality.Checkstyle",
            "org.gradle.api.tasks.javadoc.Javadoc",
            "org.gradle.api.plugins.antlr.AntlrTask",
            "org.gradle.api.tasks.scala.ScalaDoc",
            "org.gradle.api.plugins.quality.Pmd",
            "org.gradle.api.tasks.javadoc.Groovydoc",
            "org.gradle.api.tasks.compile.AbstractCompile",
            "org.gradle.api.plugins.quality.CodeNarc",
            "org.gradle.api.tasks.compile.JavaCompile",
            "org.gradle.api.tasks.compile.GroovyCompile",
            "org.gradle.language.scala.tasks.AbstractScalaCompile",
            "org.gradle.api.tasks.scala.ScalaCompile",
            "org.jetbrains.kotlin.gradle.tasks.KotlinCompile",
            "org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask"
        )).put("org.gradle.api.plugins.quality.CodeQualityExtension", ImmutableList.of(
            "org.gradle.api.plugins.quality.PmdExtension",
            "org.gradle.api.plugins.quality.CodeNarcExtension",
            "org.gradle.api.plugins.quality.CheckstyleExtension"
        )).put("org.gradle.api.publish.ivy.IvyArtifact", ImmutableList.of(
            "org.gradle.api.publish.ivy.internal.artifact.AbstractIvyArtifact",
            "org.gradle.api.publish.ivy.internal.artifact.DerivedIvyArtifact",
            "org.gradle.api.publish.ivy.internal.artifact.SingleOutputTaskIvyArtifact",
            "org.gradle.api.publish.ivy.internal.artifact.PublishArtifactBasedIvyArtifact",
            "org.gradle.api.publish.ivy.internal.artifact.ArchiveTaskBasedIvyArtifact",
            "org.gradle.api.publish.ivy.internal.artifact.FileBasedIvyArtifact"
        )).put("org.gradle.api.publish.ivy.IvyModuleDescriptorSpec", ImmutableList.of(
            "org.gradle.api.publish.ivy.internal.publication.IvyModuleDescriptorSpecInternal",
            "org.gradle.api.publish.ivy.internal.publication.DefaultIvyModuleDescriptorSpec"
        )).put("org.gradle.api.publish.ivy.IvyPublication", ImmutableList.of(
            "org.gradle.api.publish.ivy.internal.publication.IvyPublicationInternal",
            "org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublication"
        )).put("org.gradle.api.publish.maven.MavenArtifact", ImmutableList.of(
            "org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication$SerializableMavenArtifact",
            "org.gradle.api.publish.maven.internal.artifact.AbstractMavenArtifact",
            "org.gradle.api.publish.maven.internal.artifact.DerivedMavenArtifact",
            "org.gradle.api.publish.maven.internal.artifact.ArchiveTaskBasedMavenArtifact",
            "org.gradle.api.publish.maven.internal.artifact.FileBasedMavenArtifact",
            "org.gradle.api.publish.maven.internal.artifact.PublishArtifactBasedMavenArtifact",
            "org.gradle.api.publish.maven.internal.artifact.SingleOutputTaskMavenArtifact"
        )).put("org.gradle.api.publish.maven.MavenPom", ImmutableList.of(
            "org.gradle.api.publish.maven.internal.publication.MavenPomInternal",
            "org.gradle.api.publish.maven.internal.publication.DefaultMavenPom"
        )).put("org.gradle.api.publish.maven.MavenPublication", ImmutableList.of(
            "org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal",
            "org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication"
        )).put("org.gradle.api.publish.maven.tasks.AbstractPublishToMaven", ImmutableList.of(
            "org.gradle.api.publish.maven.tasks.PublishToMavenRepository",
            "org.gradle.api.publish.maven.tasks.PublishToMavenLocal"
        )).put("org.gradle.api.reporting.DirectoryReport", ImmutableList.of(
            "org.gradle.api.tasks.testing.JUnitXmlReport",
            "org.gradle.api.reporting.internal.TaskGeneratedSingleDirectoryReport",
            "org.gradle.api.internal.tasks.testing.DefaultJUnitXmlReport"
        )).put("org.gradle.api.reporting.Report", ImmutableList.of(
            "org.gradle.api.reporting.ConfigurableReport",
            "org.gradle.api.reporting.SingleFileReport",
            "org.gradle.api.reporting.internal.SimpleReport",
            "org.gradle.api.reporting.DirectoryReport",
            "org.gradle.api.reporting.CustomizableHtmlReport",
            "org.gradle.api.reporting.internal.TaskGeneratedSingleFileReport",
            "org.gradle.api.reporting.internal.TaskGeneratedReport",
            "org.gradle.api.tasks.testing.JUnitXmlReport",
            "org.gradle.api.reporting.internal.TaskGeneratedSingleDirectoryReport",
            "org.gradle.api.reporting.internal.CustomizableHtmlReportImpl",
            "org.gradle.api.internal.tasks.testing.DefaultJUnitXmlReport"
        )).put("org.gradle.api.tasks.AbstractCopyTask", ImmutableList.of(
            "org.gradle.api.tasks.Sync",
            "org.gradle.api.tasks.bundling.AbstractArchiveTask",
            "org.gradle.api.tasks.Copy",
            "org.gradle.api.tasks.bundling.Tar",
            "org.gradle.api.tasks.bundling.Zip",
            "org.gradle.language.jvm.tasks.ProcessResources",
            "org.gradle.jvm.tasks.Jar",
            "org.gradle.api.tasks.bundling.Jar",
            "org.gradle.api.tasks.bundling.War",
            "org.gradle.plugins.ear.Ear"
        )).put("org.gradle.api.tasks.AbstractExecTask", ImmutableList.of(
            "org.gradle.api.tasks.Exec",
            "org.gradle.nativeplatform.test.tasks.RunTestExecutable"
        )).put("org.gradle.api.tasks.Copy", ImmutableList.of(
            "org.gradle.language.jvm.tasks.ProcessResources"
        )).put("org.gradle.api.tasks.SourceSet", ImmutableList.of(
            "org.gradle.api.internal.tasks.DefaultSourceSet"
        )).put("org.gradle.api.tasks.SourceSetOutput", ImmutableList.of(
            "org.gradle.api.internal.tasks.DefaultSourceSetOutput"
        )).put("org.gradle.api.tasks.bundling.AbstractArchiveTask", ImmutableList.of(
            "org.gradle.api.tasks.bundling.Tar",
            "org.gradle.api.tasks.bundling.Zip",
            "org.gradle.jvm.tasks.Jar",
            "org.gradle.api.tasks.bundling.Jar",
            "org.gradle.api.tasks.bundling.War",
            "org.gradle.plugins.ear.Ear"
        )).put("org.gradle.api.tasks.bundling.Zip", ImmutableList.of(
            "org.gradle.jvm.tasks.Jar",
            "org.gradle.api.tasks.bundling.Jar",
            "org.gradle.api.tasks.bundling.War",
            "org.gradle.plugins.ear.Ear"
        )).put("org.gradle.api.tasks.compile.AbstractCompile", ImmutableList.of(
            "org.gradle.api.tasks.compile.JavaCompile",
            "org.gradle.api.tasks.compile.GroovyCompile",
            "org.gradle.language.scala.tasks.AbstractScalaCompile",
            "org.gradle.api.tasks.scala.ScalaCompile",
            "org.jetbrains.kotlin.gradle.tasks.KotlinCompile",
            "org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask"
        )).put("org.gradle.api.tasks.compile.BaseForkOptions", ImmutableList.of(
            "org.gradle.api.tasks.compile.ProviderAwareCompilerDaemonForkOptions",
            "org.gradle.api.internal.tasks.compile.MinimalCompilerDaemonForkOptions",
            "org.gradle.api.tasks.compile.ForkOptions",
            "org.gradle.api.tasks.scala.ScalaForkOptions",
            "org.gradle.api.tasks.compile.GroovyForkOptions",
            "org.gradle.api.internal.tasks.compile.MinimalJavaCompilerDaemonForkOptions",
            "org.gradle.api.internal.tasks.compile.MinimalGroovyCompilerDaemonForkOptions",
            "org.gradle.api.internal.tasks.scala.MinimalScalaCompilerDaemonForkOptions"
        )).put("org.gradle.api.tasks.compile.ProviderAwareCompilerDaemonForkOptions", ImmutableList.of(
            "org.gradle.api.tasks.compile.ForkOptions",
            "org.gradle.api.tasks.scala.ScalaForkOptions",
            "org.gradle.api.tasks.compile.GroovyForkOptions"
        )).put("org.gradle.api.tasks.diagnostics.AbstractDependencyReportTask", ImmutableList.of(
            "org.gradle.api.tasks.diagnostics.DependencyReportTask"
        )).put("org.gradle.api.tasks.diagnostics.ConventionReportTask", ImmutableList.of(
            "org.gradle.api.tasks.diagnostics.TaskReportTask",
            "org.gradle.api.tasks.diagnostics.AbstractProjectBasedReportTask",
            "org.gradle.api.tasks.diagnostics.ProjectBasedReportTask",
            "org.gradle.api.tasks.diagnostics.ProjectReportTask",
            "org.gradle.api.tasks.diagnostics.PropertyReportTask",
            "org.gradle.api.tasks.diagnostics.AbstractDependencyReportTask",
            "org.gradle.api.tasks.diagnostics.DependencyReportTask"
        )).put("org.gradle.api.tasks.testing.AbstractTestTask", ImmutableList.of(
            "org.gradle.nativeplatform.test.xctest.tasks.XCTest",
            "org.gradle.api.tasks.testing.Test"
        )).put("org.gradle.api.tasks.testing.JUnitXmlReport", ImmutableList.of(
            "org.gradle.api.internal.tasks.testing.DefaultJUnitXmlReport"
        )).put("org.gradle.api.tasks.testing.TestFilter", ImmutableList.of(
            "org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter"
        )).put("org.gradle.api.tasks.testing.logging.TestLogging", ImmutableList.of(
            "org.gradle.api.internal.tasks.testing.logging.DefaultTestLogging",
            "org.gradle.api.tasks.testing.logging.TestLoggingContainer",
            "org.gradle.api.internal.tasks.testing.logging.DefaultTestLoggingContainer"
        )).put("org.gradle.api.tasks.testing.logging.TestLoggingContainer", ImmutableList.of(
            "org.gradle.api.internal.tasks.testing.logging.DefaultTestLoggingContainer"
        )).put("org.gradle.external.javadoc.CoreJavadocOptions", ImmutableList.of(
            "org.gradle.external.javadoc.StandardJavadocDocletOptions"
        )).put("org.gradle.external.javadoc.MinimalJavadocOptions", ImmutableList.of(
            "org.gradle.external.javadoc.CoreJavadocOptions",
            "org.gradle.external.javadoc.StandardJavadocDocletOptions"
        )).put("org.gradle.jvm.application.tasks.CreateStartScripts", ImmutableList.of(
            "org.gradle.api.tasks.application.CreateStartScripts"
        )).put("org.gradle.jvm.tasks.Jar", ImmutableList.of(
            "org.gradle.api.tasks.bundling.Jar",
            "org.gradle.api.tasks.bundling.War",
            "org.gradle.plugins.ear.Ear"
        )).put("org.gradle.language.nativeplatform.tasks.AbstractNativeCompileTask", ImmutableList.of(
            "org.gradle.language.nativeplatform.tasks.AbstractNativePCHCompileTask",
            "org.gradle.language.nativeplatform.tasks.AbstractNativeSourceCompileTask",
            "org.gradle.language.cpp.tasks.CppPreCompiledHeaderCompile",
            "org.gradle.language.objectivec.tasks.ObjectiveCPreCompiledHeaderCompile",
            "org.gradle.language.c.tasks.CPreCompiledHeaderCompile",
            "org.gradle.language.objectivecpp.tasks.ObjectiveCppPreCompiledHeaderCompile",
            "org.gradle.language.objectivec.tasks.ObjectiveCCompile",
            "org.gradle.language.cpp.tasks.CppCompile",
            "org.gradle.language.c.tasks.CCompile",
            "org.gradle.language.objectivecpp.tasks.ObjectiveCppCompile"
        )).put("org.gradle.language.nativeplatform.tasks.AbstractNativeSourceCompileTask", ImmutableList.of(
            "org.gradle.language.objectivec.tasks.ObjectiveCCompile",
            "org.gradle.language.cpp.tasks.CppCompile",
            "org.gradle.language.c.tasks.CCompile",
            "org.gradle.language.objectivecpp.tasks.ObjectiveCppCompile"
        )).put("org.gradle.language.scala.tasks.AbstractScalaCompile", ImmutableList.of(
            "org.gradle.api.tasks.scala.ScalaCompile"
        )).put("org.gradle.language.scala.tasks.BaseScalaCompileOptions", ImmutableList.of(
            "org.gradle.api.tasks.scala.ScalaCompileOptions"
        )).put("org.gradle.nativeplatform.tasks.AbstractLinkTask", ImmutableList.of(
            "org.gradle.nativeplatform.tasks.LinkExecutable",
            "org.gradle.nativeplatform.tasks.LinkSharedLibrary",
            "org.gradle.nativeplatform.tasks.LinkMachOBundle"
        )).put("org.gradle.plugins.ear.descriptor.DeploymentDescriptor", ImmutableList.of(
            "org.gradle.plugins.ear.descriptor.internal.DefaultDeploymentDescriptor"
        )).put("org.gradle.plugins.ear.descriptor.EarModule", ImmutableList.of(
            "org.gradle.plugins.ear.descriptor.internal.DefaultEarModule",
            "org.gradle.plugins.ear.descriptor.EarWebModule",
            "org.gradle.plugins.ear.descriptor.internal.DefaultEarWebModule"
        )).put("org.gradle.plugins.ear.descriptor.EarSecurityRole", ImmutableList.of(
            "org.gradle.plugins.ear.descriptor.internal.DefaultEarSecurityRole"
        )).put("org.gradle.plugins.ear.descriptor.EarWebModule", ImmutableList.of(
            "org.gradle.plugins.ear.descriptor.internal.DefaultEarWebModule"
        )).put("org.gradle.plugins.signing.SignatureSpec", ImmutableList.of(
            "org.gradle.plugins.signing.Sign",
            "org.gradle.plugins.signing.SignOperation",
            "org.gradle.plugins.signing.internal.SignOperationInternal"
        )).put("org.gradle.process.BaseExecSpec", ImmutableList.of(
            "org.gradle.process.internal.AbstractExecHandleBuilder",
            "org.gradle.api.internal.provider.sources.process.DelegatingBaseExecSpec",
            "org.gradle.process.ExecSpec",
            "org.gradle.process.internal.ExecHandleBuilder",
            "org.gradle.process.JavaExecSpec",
            "org.gradle.process.internal.DefaultExecHandleBuilder",
            "org.gradle.process.internal.JavaExecHandleBuilder",
            "org.gradle.api.internal.provider.sources.process.DelegatingExecSpec",
            "org.gradle.api.internal.provider.sources.process.ProviderCompatibleBaseExecSpec",
            "org.gradle.api.internal.provider.sources.process.DelegatingJavaExecSpec",
            "org.gradle.process.internal.DefaultExecSpec",
            "org.gradle.process.internal.ExecAction",
            "org.gradle.api.tasks.AbstractExecTask",
            "org.gradle.process.internal.JavaExecAction",
            "org.gradle.process.internal.DefaultJavaExecSpec",
            "org.gradle.api.tasks.JavaExec",
            "org.gradle.process.internal.DefaultExecAction",
            "org.gradle.process.internal.DefaultJavaExecAction",
            "org.gradle.api.internal.provider.sources.process.ProviderCompatibleExecSpec",
            "org.gradle.api.internal.provider.sources.process.ProviderCompatibleJavaExecSpec",
            "org.gradle.api.tasks.Exec",
            "org.gradle.nativeplatform.test.tasks.RunTestExecutable"
        )).put("org.gradle.process.ExecSpec", ImmutableList.of(
            "org.gradle.api.internal.provider.sources.process.DelegatingExecSpec",
            "org.gradle.process.internal.ExecHandleBuilder",
            "org.gradle.process.internal.DefaultExecSpec",
            "org.gradle.process.internal.ExecAction",
            "org.gradle.api.tasks.AbstractExecTask",
            "org.gradle.api.internal.provider.sources.process.ProviderCompatibleExecSpec",
            "org.gradle.process.internal.DefaultExecHandleBuilder",
            "org.gradle.process.internal.DefaultExecAction",
            "org.gradle.api.tasks.Exec",
            "org.gradle.nativeplatform.test.tasks.RunTestExecutable"
        )).put("org.gradle.process.JavaExecSpec", ImmutableList.of(
            "org.gradle.process.internal.JavaExecAction",
            "org.gradle.process.internal.DefaultJavaExecSpec",
            "org.gradle.process.internal.JavaExecHandleBuilder",
            "org.gradle.api.tasks.JavaExec",
            "org.gradle.api.internal.provider.sources.process.DelegatingJavaExecSpec",
            "org.gradle.process.internal.DefaultJavaExecAction",
            "org.gradle.api.internal.provider.sources.process.ProviderCompatibleJavaExecSpec"
        )).put("org.gradle.process.JavaForkOptions", ImmutableList.of(
            "org.gradle.process.internal.JavaForkOptionsInternal",
            "org.gradle.process.JavaExecSpec",
            "org.gradle.api.tasks.testing.Test",
            "org.gradle.process.internal.DefaultJavaForkOptions",
            "org.gradle.process.internal.DefaultExecActionFactory$ImmutableJavaForkOptions",
            "org.gradle.process.internal.JavaExecAction",
            "org.gradle.process.internal.DefaultJavaExecSpec",
            "org.gradle.process.internal.JavaExecHandleBuilder",
            "org.gradle.api.tasks.JavaExec",
            "org.gradle.api.internal.provider.sources.process.DelegatingJavaExecSpec",
            "org.gradle.process.internal.DefaultJavaExecAction",
            "org.gradle.api.internal.provider.sources.process.ProviderCompatibleJavaExecSpec"
        )).put("org.gradle.process.ProcessForkOptions", ImmutableList.of(
            "org.gradle.process.internal.DefaultProcessForkOptions",
            "org.gradle.process.BaseExecSpec",
            "org.gradle.process.internal.ExecHandleBuilder",
            "org.gradle.process.JavaForkOptions",
            "org.gradle.process.internal.DefaultJavaForkOptions",
            "org.gradle.process.internal.AbstractExecHandleBuilder",
            "org.gradle.process.internal.DefaultExecSpec",
            "org.gradle.api.internal.provider.sources.process.DelegatingBaseExecSpec",
            "org.gradle.process.ExecSpec",
            "org.gradle.process.JavaExecSpec",
            "org.gradle.process.internal.DefaultExecHandleBuilder",
            "org.gradle.process.internal.JavaForkOptionsInternal",
            "org.gradle.api.tasks.testing.Test",
            "org.gradle.process.internal.DefaultJavaExecSpec",
            "org.gradle.process.internal.JavaExecHandleBuilder",
            "org.gradle.api.internal.provider.sources.process.DelegatingExecSpec",
            "org.gradle.api.internal.provider.sources.process.ProviderCompatibleBaseExecSpec",
            "org.gradle.api.internal.provider.sources.process.DelegatingJavaExecSpec",
            "org.gradle.process.internal.ExecAction",
            "org.gradle.api.tasks.AbstractExecTask",
            "org.gradle.process.internal.JavaExecAction",
            "org.gradle.api.tasks.JavaExec",
            "org.gradle.process.internal.DefaultExecAction",
            "org.gradle.process.internal.DefaultExecActionFactory$ImmutableJavaForkOptions",
            "org.gradle.process.internal.DefaultJavaExecAction",
            "org.gradle.api.internal.provider.sources.process.ProviderCompatibleExecSpec",
            "org.gradle.api.internal.provider.sources.process.ProviderCompatibleJavaExecSpec",
            "org.gradle.api.tasks.Exec",
            "org.gradle.nativeplatform.test.tasks.RunTestExecutable"
        )).put("org.gradle.testing.jacoco.tasks.JacocoBase", ImmutableList.of(
            "org.gradle.testing.jacoco.tasks.JacocoMerge",
            "org.gradle.testing.jacoco.tasks.JacocoReportBase",
            "org.gradle.testing.jacoco.tasks.JacocoCoverageVerification",
            "org.gradle.testing.jacoco.tasks.JacocoReport"
        )).put("org.gradle.testing.jacoco.tasks.rules.JacocoLimit", ImmutableList.of(
            "org.gradle.internal.jacoco.rules.JacocoLimitImpl"
        )).put("org.gradle.testing.jacoco.tasks.rules.JacocoViolationRule", ImmutableList.of(
            "org.gradle.internal.jacoco.rules.JacocoViolationRuleImpl"
        )).put("org.gradle.testing.jacoco.tasks.rules.JacocoViolationRulesContainer", ImmutableList.of(
            "org.gradle.internal.jacoco.rules.JacocoViolationRulesContainerImpl"
        )).put("org.gradle.vcs.VersionControlRepository", ImmutableList.of(
            "org.gradle.vcs.internal.DefaultVersionControlRepository"
        )).put("org.gradle.vcs.VersionControlSpec", ImmutableList.of(
            "org.gradle.vcs.git.GitVersionControlSpec",
            "org.gradle.vcs.internal.spec.AbstractVersionControlSpec",
            "org.gradle.vcs.git.internal.DefaultGitVersionControlSpec"
        )).put("org.gradle.vcs.git.GitVersionControlSpec", ImmutableList.of(
            "org.gradle.vcs.git.internal.DefaultGitVersionControlSpec"
        ))
        .build();

    public ImmutableList<ReportableApiChange> parseAcceptedApiChanges(File apiChangesPath) {
        List<JsonApiChange> jsonApiChanges = parseApiChangeFile(apiChangesPath);
        return jsonApiChanges.stream()
            .map(this::mapToApiChange)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(ImmutableList.toImmutableList());
    }

    private @Nonnull Optional<ReportableApiChange> mapToApiChange(JsonApiChange jsonApiChange) {
        String member = jsonApiChange.member;
        Matcher methodMatcher = METHOD_PATTERN.matcher(member);
        if (!methodMatcher.matches()) {
            return Optional.empty();
        }

        String typeName = methodMatcher.group(2);
        String methodName = methodMatcher.group(3);
        String parameters = methodMatcher.group(4);
        List<String> parameterTypeNames = Arrays.stream(COMMA_LIST_PATTERN.split(parameters))
            .filter(split -> !split.isEmpty())
            .collect(Collectors.toList());
        Optional<Class<?>> type = getClassForName(typeName);
        if (!type.isPresent()) {
            // This means type is not on a classpath, so it doesn't need upgrade
            return Optional.empty();
        }

        List<Class<?>> parameterTypes = parameterTypeNames.stream()
            .map(name -> getClassForName(name).orElse(null))
            .collect(Collectors.toList());
        if (parameterTypes.stream().anyMatch(Objects::isNull)) {
            LOGGER.error("Cannot find all type parameters {} for upgrade {}", parameterTypeNames, member);
            return Optional.empty();
        }

        try {
            Method method = type.get().getMethod(methodName, parameterTypes.toArray(new Class[0]));
            List<String> allKnownSubtypes = ALL_KNOWN_SUBTYPES.getOrDefault(type.get().getName(), Collections.emptyList());
            String displayParameters = parameterTypes.stream()
                .map(Class::getSimpleName)
                .collect(Collectors.joining(","));
            String displayText = String.format("%s %s.%s(%s)", method.getReturnType().getSimpleName(), type.get().getName(), methodName, displayParameters);
            return Optional.of(new MethodReportableApiChange(jsonApiChange.type, allKnownSubtypes, method, displayText, jsonApiChange.acceptation, jsonApiChange.changes));
        } catch (NoSuchMethodException e) {
            // This means that method on classpath has different signature, so older/newer version is used, we can't report for
            LOGGER.error("Cannot find method for upgrade {}", member);
            return Optional.empty();
        }
    }

    private Optional<Class<?>> getClassForName(String name) {
        try {
            if (name.endsWith("[]")) {
                // TODO handle primitives also here
                String arrayName = "[L" + name.replace("[]", "") + ";";
                return Optional.of(Class.forName(arrayName));
            }
            Class<?> primitiveClass = PRIMITIVE_CLASSES.get(name);
            if (primitiveClass != null) {
                return Optional.of(primitiveClass);
            }
            return Optional.of(Class.forName(name));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private List<JsonApiChange> parseApiChangeFile(File apiChangesFile) {
        Map<String, Object> apiChangesJson = (Map<String, Object>) new JsonSlurper().parse(apiChangesFile);
        List<Map<String, Object>> acceptedApiChanges = (List<Map<String, Object>>) apiChangesJson.get("acceptedApiChanges");
        return acceptedApiChanges.stream()
            .map(this::mapToApiChange)
            .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private JsonApiChange mapToApiChange(Map<String, Object> change) {
        String type = (String) change.get("type");
        String member = (String) change.get("member");
        String acceptation = (String) change.get("acceptation");
        List<String> changes = (List<String>) change.get("changes");
        return new JsonApiChange(type, member, acceptation, changes);
    }

    private static class JsonApiChange {
        private final String type;
        private final String member;
        private final String acceptation;
        private final List<String> changes;

        private JsonApiChange(String type, String member, String acceptation, List<String> changes) {
            this.type = type;
            this.member = member;
            this.acceptation = acceptation;
            this.changes = changes;
        }
    }
}
