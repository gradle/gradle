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

package org.gradle.internal.file

import org.apache.tools.ant.DirectoryScanner
import org.gradle.BuildListener
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.initialization.RootBuildLifecycleListener
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.event.AnonymousListenerBroadcast
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.file.excludes.FileSystemDefaultExcludesListener
import org.gradle.internal.file.excludes.GradleDefaultExcludes
import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.problems.NoOpProblemDiagnosticsFactory
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

/**
 * Unit coverage for the cross-settings-script accumulator and the property-vs-Ant precedence
 * rules in {@link DefaultFileSystemDefaultExcludesProvider}. The integration tests only ever
 * add distinct patterns per build, so the interesting accumulator branches (re-adding a removed
 * default, cancelling a previous addition, and the "both APIs used" precedence cases) are
 * exercised here.
 */
class DefaultFileSystemDefaultExcludesProviderTest extends Specification {

    private static final List<String> BASELINE = GradleDefaultExcludes.DEFAULT_EXCLUDES

    private final CollectingTestOutputEventListener outputEventListener = new CollectingTestOutputEventListener()

    @Rule
    public final ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    private final List<Object> registeredListeners = []
    private final List<List<String>> broadcasts = []

    private DefaultFileSystemDefaultExcludesProvider provider

    def setup() {
        DeprecationLogger.reset()
        DeprecationLogger.init(WarningMode.All, Mock(BuildOperationProgressEventEmitter), TestUtil.problemsService(), new NoOpProblemDiagnosticsFactory().newUnlimitedStream())

        def broadcastSource = { List<String> excludes -> broadcasts << excludes } as FileSystemDefaultExcludesListener
        def broadcaster = Mock(AnonymousListenerBroadcast)
        broadcaster.getSource() >> broadcastSource
        def listenerManager = Mock(ListenerManager)
        listenerManager.createAnonymousBroadcaster(FileSystemDefaultExcludesListener) >> broadcaster
        listenerManager.addListener(_) >> { args -> registeredListeners << args[0] }

        provider = new DefaultFileSystemDefaultExcludesProvider(listenerManager)
        afterStart()
    }

    def cleanup() {
        // The provider mutates the process-global Ant DirectoryScanner; reset it so tests don't leak.
        DirectoryScanner.resetDefaultExcludes()
        DeprecationLogger.reset()
    }

    def "starts from the Gradle-owned baseline and broadcasts it"() {
        expect:
        provider.currentDefaultExcludes as Set == BASELINE as Set
        broadcasts.last() as Set == BASELINE as Set
    }

    def "property can add a custom exclude"() {
        when:
        settingsEvaluated(baselinePlus("**/node_modules"))

        then:
        provider.currentDefaultExcludes as Set == baselinePlus("**/node_modules")
        broadcasts.last() as Set == baselinePlus("**/node_modules")
        noDeprecationWarnings()
    }

    def "property can remove a built-in exclude"() {
        when:
        settingsEvaluated(baselineMinus("**/.git"))

        then:
        !provider.currentDefaultExcludes.contains("**/.git")
        provider.currentDefaultExcludes as Set == baselineMinus("**/.git")
        noDeprecationWarnings()
    }

    def "leaving the property at its default is not treated as a customization"() {
        when:
        settingsEvaluated(null)

        then:
        provider.currentDefaultExcludes as Set == BASELINE as Set
        noDeprecationWarnings()
    }

    def "additions from multiple settings scripts stack"() {
        when:
        settingsEvaluated(baselinePlus("**/a"))
        settingsEvaluated(baselinePlus("**/b"))

        then:
        provider.currentDefaultExcludes.containsAll(["**/a", "**/b"])
        provider.currentDefaultExcludes as Set == baselinePlus("**/a", "**/b")
    }

    def "a later script can re-include a default that an earlier script removed"() {
        when: "an earlier script removes a built-in default"
        settingsEvaluated(baselineMinus("**/.git"))

        then:
        !provider.currentDefaultExcludes.contains("**/.git")

        when: "a later script keeps the default while customizing something else"
        settingsEvaluated(baselinePlus("**/keep"))

        then: "the earlier removal is cancelled and the default is back"
        provider.currentDefaultExcludes.contains("**/.git")
        provider.currentDefaultExcludes.contains("**/keep")
    }

    def "a later script can cancel an addition made via the Ant path"() {
        when: "an earlier script adds an exclude by mutating the DirectoryScanner"
        DirectoryScanner.addDefaultExclude("**/x")
        settingsEvaluated(null)

        then:
        provider.currentDefaultExcludes.contains("**/x")

        when: "a later script removes it again via the DirectoryScanner"
        DirectoryScanner.removeDefaultExclude("**/x")
        settingsEvaluated(null)

        then: "the earlier addition is cancelled"
        !provider.currentDefaultExcludes.contains("**/x")
        provider.currentDefaultExcludes as Set == BASELINE as Set
    }

    def "mutating the DirectoryScanner alone is honored but deprecated"() {
        when:
        DirectoryScanner.addDefaultExclude("**/antOnly")
        settingsEvaluated(null)

        then:
        provider.currentDefaultExcludes.contains("**/antOnly")

        and:
        def warnings = deprecationWarnings()
        warnings.size() == 1
        warnings[0].contains("Mutating org.apache.tools.ant.DirectoryScanner default excludes has been deprecated")
    }

    def "when both APIs are used the property wins and the DirectoryScanner mutation is ignored"() {
        when:
        DirectoryScanner.addDefaultExclude("**/fromAnt")
        settingsEvaluated(baselinePlus("**/fromProperty"))

        then: "the property contribution is applied, the Ant mutation is dropped"
        provider.currentDefaultExcludes.contains("**/fromProperty")
        !provider.currentDefaultExcludes.contains("**/fromAnt")

        and: "the user is told the DirectoryScanner mutation was ignored"
        def warnings = deprecationWarnings()
        warnings.size() == 1
        warnings[0].contains("Configuring file-system default excludes via both")
    }

    def "restoring from the configuration cache reapplies the stored set"() {
        when:
        provider.updateCurrentDefaultExcludes(baselinePlus("**/restored"))

        then:
        provider.currentDefaultExcludes as Set == baselinePlus("**/restored")
        broadcasts.last() as Set == baselinePlus("**/restored")
    }

    def "a settings script evaluated after a configuration-cache restore stacks on the restored set"() {
        given:
        provider.updateCurrentDefaultExcludes(baselinePlus("**/restored"))

        when:
        settingsEvaluated(baselinePlus("**/later"))

        then:
        provider.currentDefaultExcludes.containsAll(["**/restored", "**/later"])
    }

    private void afterStart() {
        (registeredListeners.find { it instanceof RootBuildLifecycleListener } as RootBuildLifecycleListener).afterStart()
    }

    /**
     * Drives a {@code settingsEvaluated} callback with a {@code fileSystemDefaultExcludes} property
     * conventioned to the baseline (mirroring {@code SettingsFactory}). Pass {@code null} to leave the
     * property at its default (uncustomized); pass an explicit set to customize it.
     */
    private void settingsEvaluated(Set<String> propertyValue) {
        def property = TestUtil.objectFactory().setProperty(String)
        property.convention(BASELINE)
        if (propertyValue != null) {
            property.set(propertyValue)
        }
        def settings = Mock(Settings) {
            getFileSystemDefaultExcludes() >> property
        }
        (registeredListeners.find { it instanceof BuildListener } as BuildListener).settingsEvaluated(settings)
    }

    private List<String> deprecationWarnings() {
        outputEventListener.events.findAll { it.logLevel == LogLevel.WARN }*.message
    }

    private void noDeprecationWarnings() {
        assert deprecationWarnings().isEmpty()
    }

    private static Set<String> baselinePlus(String... extra) {
        def result = new LinkedHashSet<String>(BASELINE)
        result.addAll(extra as List)
        result
    }

    private static Set<String> baselineMinus(String... removed) {
        def result = new LinkedHashSet<String>(BASELINE)
        result.removeAll(removed as List)
        result
    }
}
