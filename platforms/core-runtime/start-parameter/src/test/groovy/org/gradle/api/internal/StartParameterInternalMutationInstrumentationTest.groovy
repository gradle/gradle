/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal

import org.gradle.StartParameter
import org.gradle.internal.buildoption.Option
import spock.lang.Specification

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.time.Duration

/**
 * Classifies every method of {@link StartParameter} and {@link StartParameterInternal} explicitly: those
 * that notify the mutation listener (via {@code onMutableCall}), those that deliberately do not, those
 * that return an intercepting collection, and plain reads.
 *
 * <p>The {@code "every method ... is classified"} test fails if the actual method set ever diverges from
 * these lists, forcing a conscious decision for any added, removed or renamed method — so a new mutator
 * cannot slip through un-instrumented. End-to-end reporting of each mutator (and of mutating the
 * collection views) is verified by {@code IsolatedProjectsStartParameterIntegrationTest}.
 */
class StartParameterInternalMutationInstrumentationTest extends Specification {

    // Mutators that notify on every call, reported as violations after settings evaluation under IP.
    private static final List<String> NOTIFYING = [
        "addInitScript(File)",
        "doNotSearchUpwards()",
        "enableProblemReportGeneration(boolean)",
        "includeBuild(File)",
        "setBuildCacheDebugLogging(boolean)",
        "setBuildCacheEnabled(boolean)",
        "setBuildProjectDependencies(boolean)",
        "setBuildScan(boolean)",
        "setConfigurationCache(Value)",
        "setConfigurationCacheDebug(boolean)",
        "setConfigurationCacheEntriesPerKey(int)",
        "setConfigurationCacheFineGrainedPropertyTracking(boolean)",
        "setConfigurationCacheHeapDumpDir(String)",
        "setConfigurationCacheIgnoreInputsDuringStore(boolean)",
        "setConfigurationCacheIgnoreUnsupportedBuildEventsListeners(boolean)",
        "setConfigurationCacheIgnoredFileSystemCheckInputs(String)",
        "setConfigurationCacheIntegrityCheckEnabled(boolean)",
        "setConfigurationCacheMaxProblems(int)",
        "setConfigurationCacheParallel(boolean)",
        "setConfigurationCacheProblems(Value)",
        "setConfigurationCacheQuiet(boolean)",
        "setConfigurationCacheReadOnly(boolean)",
        "setConfigurationCacheRecreateCache(boolean)",
        "setConfigureOnDemand(boolean)",
        "setConsoleOutput(ConsoleOutput)",
        "setConsoleUnicodeSupport(ConsoleUnicodeSupport)",
        "setContinueOnFailure(boolean)",
        "setContinuous(boolean)",
        "setContinuousBuildQuietPeriod(Duration)",
        "setCurrentDir(File)",
        "setDaemonJvmCriteriaConfigured(boolean)",
        "setDependencyVerificationMode(DependencyVerificationMode)",
        "setDevelocityPluginVersion(String)",
        "setDevelocityUrl(String)",
        "setDryRun(boolean)",
        "setExcludedTaskNames(Iterable)",
        "setExportKeys(boolean)",
        "setGradleHomeDir(File)",
        "setGradleUserHomeDir(File)",
        "setIncludedBuilds(List)",
        "setInitScripts(List)",
        "setInteractive(boolean)",
        "setIsolatedProjects(Value)",
        "setIsolatedProjectsDangerouslyIgnoreProblems(boolean)",
        "setIsolatedProjectsDiagnostics(boolean)",
        "setLockedDependenciesToUpdate(List)",
        "setLogLevel(LogLevel)",
        "setMaxWorkerCount(int)",
        "setNoBuildScan(boolean)",
        "setOffline(boolean)",
        "setParallelProjectExecutionEnabled(boolean)",
        "setParallelToolingModelBuilding(Value)",
        "setProfile(boolean)",
        "setProjectCacheDir(File)",
        "setProjectDir(File)",
        "setProjectProperties(Map)",
        "setPropertyUpgradeReportEnabled(boolean)",
        "setRefreshDependencies(boolean)",
        "setRefreshKeys(boolean)",
        "setRerunTasks(boolean)",
        "setShowStacktrace(ShowStacktrace)",
        "setSystemPropertiesArgs(Map)",
        "setTaskGraph(boolean)",
        "setVfsVerboseLogging(boolean)",
        "setWarningMode(WarningMode)",
        "setWatchFileSystemMode(WatchMode)",
        "setWelcomeMessageConfiguration(WelcomeMessageConfiguration)",
        "setWriteDependencyLocks(boolean)",
        "setWriteDependencyVerifications(List)",
        "useEmptySettings()",
    ]

    // Mutators that deliberately do not notify, plus the listener-wiring infrastructure.
    private static final List<String> TASK_REQUEST_MODIFIER = [
        // Tooling model builders legitimately replace the requested tasks while the build runs.
        "setTaskNames(Iterable)",
        "setTaskRequests(Iterable)",
    ]
    // Mutators that deliberately do not notify, plus the listener-wiring infrastructure.

    private static final List<String> NOT_NOTIFYING = TASK_REQUEST_MODIFIER + [
        // Listener wiring, not tracked state.
        "setMutationListener(Consumer)",
        "clearMutationListener()",
    ]

    // Getters returning intercepting collections; mutating the returned view notifies through the view.
    private static final List<String> INTERCEPTING_COLLECTIONS = [
        "getTaskRequests()",
        "getExcludedTaskNames()",
        "getProjectProperties()",
        "getSystemPropertiesArgs()",
        "getWriteDependencyVerifications()",
        "getLockedDependenciesToUpdate()",
    ]

    // Reads and other non-mutating methods (queries, copies, Object overrides).
    private static final List<String> READS = [
        "equals(Object)",
        "getAllInitScripts()",
        "getConfigurationCache()",
        "getConfigurationCacheEntriesPerKey()",
        "getConfigurationCacheHeapDumpDir()",
        "getConfigurationCacheIgnoredFileSystemCheckInputs()",
        "getConfigurationCacheMaxProblems()",
        "getConfigurationCacheProblems()",
        "getConsoleOutput()",
        "getConsoleUnicodeSupport()",
        "getContinuousBuildQuietPeriod()",
        "getCurrentDir()",
        "getDependencyVerificationMode()",
        "getDevelocityPluginVersion()",
        "getDevelocityUrl()",
        "getGradleHomeDir()",
        "getGradleUserHomeDir()",
        "getIncludedBuilds()",
        "getInitScripts()",
        "getIsolatedProjects()",
        "getLogLevel()",
        "getMaxWorkerCount()",
        "getParallelToolingModelBuilding()",
        "getProjectCacheDir()",
        "getProjectDir()",
        "getProjectPropertiesUntracked()",
        "getShowStacktrace()",
        "getTaskNames()",
        "getWarningMode()",
        "getWatchFileSystemMode()",
        "getWelcomeMessageConfiguration()",
        "hashCode()",
        "isBuildCacheDebugLogging()",
        "isBuildCacheEnabled()",
        "isBuildProjectDependencies()",
        "isBuildScan()",
        "isConfigurationCacheDebug()",
        "isConfigurationCacheFineGrainedPropertyTracking()",
        "isConfigurationCacheIgnoreInputsDuringStore()",
        "isConfigurationCacheIgnoreUnsupportedBuildEventsListeners()",
        "isConfigurationCacheIntegrityCheckEnabled()",
        "isConfigurationCacheParallel()",
        "isConfigurationCacheQuiet()",
        "isConfigurationCacheReadOnly()",
        "isConfigurationCacheRecreateCache()",
        "isConfigurationCacheRequested()",
        "isConfigureOnDemand()",
        "isContinueOnFailure()",
        "isContinuous()",
        "isDaemonJvmCriteriaConfigured()",
        "isDryRun()",
        "isExportKeys()",
        "isInteractive()",
        "isIsolatedProjectsDangerouslyIgnoreProblems()",
        "isIsolatedProjectsDiagnostics()",
        "isNoBuildScan()",
        "isOffline()",
        "isParallelProjectExecutionEnabled()",
        "isProblemReportGenerationEnabled()",
        "isProfile()",
        "isPropertyUpgradeReportEnabled()",
        "isRefreshDependencies()",
        "isRefreshKeys()",
        "isRerunTasks()",
        "isSearchUpwards()",
        "isTaskGraph()",
        "isUseEmptySettings()",
        "isVfsVerboseLogging()",
        "isWriteDependencyLocks()",
        "newBuild()",
        "newBuildInternal()",
        "newInstance()",
        "newInstanceInternal()",
        "toBuildLayoutConfiguration()",
        "toString()",
    ]

    def "every method of StartParameter(Internal) is classified exactly once"() {
        given:
        def classified = NOTIFYING + NOT_NOTIFYING + INTERCEPTING_COLLECTIONS + READS

        expect:
        // No method appears in more than one list.
        classified.size() == (classified as Set).size()

        and:
        // The classification matches the actual API exactly. If this fails, a method was added, removed
        // or renamed: classify it (notifying / not-notifying / intercepting-collection / read). If it is
        // a new mutator, also cover it in IsolatedProjectsStartParameterIntegrationTest.
        def actual = declaredSignatures()
        def unclassified = actual - (classified as Set)
        def stale = (classified as Set) - actual
        assert unclassified.isEmpty(): "Unclassified methods of StartParameter(Internal): $unclassified"
        assert stale.isEmpty(): "Listed methods that no longer exist: $stale"
    }

    def "every notifying mutator calls onMutableCall"() {
        given:
        def notified = []
        def parameter = new StartParameterInternal()
        parameter.setMutationListener { notified << it }
        def bySignature = declaredMethods().collectEntries { [signature(it), it] }

        expect:
        NOTIFYING.each { sig ->
            def method = bySignature[sig] as Method
            notified.clear()
            try {
                method.invoke(parameter, argumentsFor(method))
            } catch (InvocationTargetException ignored) {
                // A setter may validate its argument and throw; the contract is "notify first, then mutate".
            }
            assert !notified.isEmpty(): "$sig is listed as notifying but does not call onMutableCall()"
        }
    }

    def "exempt mutators do not notify"() {
        given:
        def notified = []
        def parameter = new StartParameterInternal()
        parameter.setMutationListener { notified << it }
        def bySignature = declaredMethods().collectEntries { [signature(it), it] }

        expect:
        TASK_REQUEST_MODIFIER.each { sig ->
            def method = bySignature[sig] as Method
            notified.clear()
            method.invoke(parameter, argumentsFor(method))
            assert notified.isEmpty(): "$sig is exempt but notified a mutation"
        }
    }

    def "mutating a returned intercepting collection notifies through its view"() {
        given:
        def notified = []
        def parameter = new StartParameterInternal()
        parameter.setMutationListener { notified << it }
        def bySignature = declaredMethods().collectEntries { [signature(it), it] }

        expect:
        INTERCEPTING_COLLECTIONS.each { getterSig ->
            def getter = bySignature[getterSig] as Method
            def view = getter.invoke(parameter)
            notified.clear()
            try {
                view.clear()
            } catch (RuntimeException ignored) {
                // The backing collection may reject the mutation (some default to an empty immutable
                // collection); the contract is "notify first, then mutate", so it still notifies.
            }
            assert notified == [getterSig + ".clear()"]: "mutating the $getterSig view did not notify"
        }
    }

    def "reads do not notify"() {
        given:
        def notified = []
        def parameter = new StartParameterInternal()
        parameter.setMutationListener { notified << it }
        def bySignature = declaredMethods().collectEntries { [signature(it), it] }

        expect:
        READS.each { sig ->
            def method = bySignature[sig] as Method
            notified.clear()
            try {
                method.invoke(parameter, argumentsFor(method))
            } catch (InvocationTargetException ignored) {
                // A read may reject its sample argument; we only care that it does not notify.
            }
            assert notified.isEmpty(): "$sig is listed as a read but notified a mutation"
        }
    }

    private static Set<String> declaredSignatures() {
        declaredMethods().collect { signature(it) }.toSet()
    }

    private static List<Method> declaredMethods() {
        (StartParameter.methods.toList() + StartParameterInternal.methods.toList())
            .findAll { !Modifier.isStatic(it.modifiers) && !it.synthetic && it.declaringClass != Object }
    }

    private static Object[] argumentsFor(Method m) {
        m.parameterTypes.collect { sampleValue(it) } as Object[]
    }

    private static Object sampleValue(Class<?> type) {
        if (type == boolean.class) {
            return false
        }
        if (type == int.class) {
            return 1
        }
        if (type == File.class) {
            return new File(".")
        }
        if (type == String.class) {
            return ""
        }
        if (type == Duration.class) {
            return Duration.ZERO
        }
        if (type == Option.Value.class) {
            return Option.Value.defaultValue(false)
        }
        if (type.isEnum()) {
            return type.enumConstants[0]
        }
        if (Map.isAssignableFrom(type)) {
            return [:]
        }
        if (Iterable.isAssignableFrom(type)) {
            return []
        }
        if (type.isPrimitive()) {
            throw new IllegalArgumentException("No sample value for primitive ${type.name}; extend sampleValue()")
        }
        // Any other reference type: null is enough, since onMutableCall fires before the argument is used
        // and we tolerate the setter throwing afterwards.
        return null
    }

    private static String signature(Method m) {
        "${m.name}(${m.parameterTypes.collect { it.simpleName }.join(', ')})"
    }
}
