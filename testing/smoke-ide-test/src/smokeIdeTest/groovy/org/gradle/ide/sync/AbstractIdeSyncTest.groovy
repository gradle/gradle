/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.ide.sync

import com.google.common.collect.ImmutableList
import org.gradle.ide.sync.fixtures.IsolatedProjectsIdeSyncFixture
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.internal.jvm.Jvm
import org.gradle.profiler.BuildAction
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.GradleBuildConfiguration
import org.gradle.profiler.InvocationSettings
import org.gradle.profiler.Logging
import org.gradle.profiler.Profiler
import org.gradle.profiler.gradle.DaemonControl
import org.gradle.profiler.gradle.GradleBuildInvoker
import org.gradle.profiler.gradle.GradleScenarioDefinition
import org.gradle.profiler.gradle.GradleScenarioInvoker
import org.gradle.profiler.ide.IdeConfiguration
import org.gradle.profiler.ide.IdeSyncAction
import org.gradle.profiler.ide.IdeType
import org.gradle.profiler.ide.invoker.IdeGradleScenarioDefinition
import org.gradle.profiler.ide.invoker.IdeGradleScenarioInvoker
import org.gradle.profiler.ide.tools.AndroidStudioFinder
import org.gradle.profiler.ide.tools.IntellijFinder
import org.gradle.profiler.instrument.PidInstrumentation
import org.gradle.profiler.report.Format
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Tests that runs a project import to IDE, with an provisioning of the desired version.
 */
abstract class AbstractIdeSyncTest extends Specification {

    @TempDir
    File testDirectory

    private final GradleDistribution distribution = new UnderDevelopmentGradleDistribution(getBuildContext())

    IntegrationTestBuildContext getBuildContext() {
        return IntegrationTestBuildContext.INSTANCE
    }

    IsolatedProjectsIdeSyncFixture getReport() {
        return new IsolatedProjectsIdeSyncFixture(projectDirectory)
    }

    List<String> ideJvmArgs = []

    /**
     * Runs a full Android Studio sync using Gradle Profiler.
     * The Android Studio installation is resolved by {@link AndroidStudioFinder}.
     * Optionally, {@link BuildMutator}s may be provided for incremental sync testing.
     * When mutators are provided, two syncs are performed: initial import + re-sync after mutation.
     */
    protected void androidStudioSync(List<BuildMutator> mutators = []) {
        ideSync(IdeType.ANDROID_STUDIO, AndroidStudioFinder.findStudioHome(), mutators)
    }

    /**
     * Runs a full sync with IntelliJ IDEA Ultimate as an external process.
     * The IDE distribution is provisioned by IdeProvisioningPlugin.
     */
    protected void ideaSync(List<BuildMutator> mutators = []) {
        ideSync(IdeType.INTELLIJ_IDEA, IntellijFinder.findIdeHome(), mutators)
    }

    private void ideSync(IdeType ide, File ideInstallDir, List<BuildMutator> buildMutators) {
        def gradleUserHome = new File(testDirectory, "gradle-user-home")
        def ideSandboxDir = new File(testDirectory, "ide-sandbox")
        def outputDir = new File(testDirectory, "profiler-output")
        outputDir.mkdirs()

        def hasMutators = !buildMutators.isEmpty()

        def invocationSettings = ideSyncInvocationSettingsBuilder(ide, new IdeConfiguration(ideInstallDir, ideSandboxDir), hasMutators)
            .setProjectDir(projectDirectory)
            .setProfiler(Profiler.NONE)
            .setBenchmark(true)
            .setOutputDir(outputDir)
            .setScenarioFile(null)
            .setSysProperties(Collections.emptyMap())
            .setCsvFormat(Format.LONG)
            .setBuildOperationsTrace(false)
            .setInvoker(GradleBuildInvoker.Ide)
            .setDryRun(false)
            .setVersions(ImmutableList.of(distribution.version.version))
            .setTargets(Collections.emptyList())
            .setGradleUserHome(gradleUserHome)
            .setMeasureConfigTime(false)
            .setBuildOperationMeasurements(Collections.emptyList())
            .setMeasureGarbageCollection(false)
            .build()

        def gradleBuildConfig = new GradleBuildConfiguration(
            distribution.version,
            distribution.gradleHomeDir,
            Jvm.current().javaHome,
            ImmutableList.of(),
            false,
            false,
            ImmutableList.of()
        )

        def scenarioDefinition = new GradleScenarioDefinition(
            "ide-sync-test",
            "IDE Sync Test",
            GradleBuildInvoker.Ide,
            gradleBuildConfig,
            new IdeSyncAction(),
            BuildAction.NO_OP,
            ImmutableList.of(),
            Collections.emptyMap(),
            buildMutators,
            hasMutators ? 1 : 0, // we support 0 warmups in single-shot mode
            1,
            outputDir,
            ImmutableList.of(),
            ImmutableList.of(),
            false
        )

        def ideScenarioDefinition = new IdeGradleScenarioDefinition(
            scenarioDefinition,
            ide,
            ideJvmArgs,
            ImmutableList.of()
        )

        def daemonControl = new DaemonControl(gradleUserHome)
        def pidInstrumentation = new PidInstrumentation()
        def gradleInvoker = new GradleScenarioInvoker(daemonControl, pidInstrumentation)
        def ideInvoker = new IdeGradleScenarioInvoker(gradleInvoker)

        Logging.setupLogging(testDirectory)
        try {
            ideInvoker.run(ideScenarioDefinition, invocationSettings, {})
        } finally {
            Logging.resetLogging()
        }
    }

    private static InvocationSettings.InvocationSettingsBuilder ideSyncInvocationSettingsBuilder(
        IdeType ideType,
        IdeConfiguration ideConfiguration,
        boolean hasMutators
    ) {
        def builder = new InvocationSettings.InvocationSettingsBuilder()
        builder = ideType == IdeType.ANDROID_STUDIO
            ? builder.setStudioConfiguration(ideConfiguration)
            : builder.setIdeaConfiguration(ideConfiguration)
        builder = hasMutators
            ? builder.setWarmupCount(1).setIterations(1)
            : builder.setSingleShot(true)
        return builder
    }

    protected TestFile getProjectDirectory() {
        new TestFile(testDirectory, "project-under-test").createDir()
    }

    protected TestFile projectFile(Object... path) {
        if (path.length == 1 && path[0] instanceof TestFile) {
            return path[0] as TestFile
        }
        projectDirectory.file(path)
    }

}
