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

package org.gradle.internal.cc.impl

import groovy.io.FileType
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import spock.lang.Issue

/**
 * Verifies that the transformations a third-party {@code -javaagent:} applies to buildscript classpath classes
 * survive restoring the build from the configuration cache: the composition of Gradle instrumentation with the
 * agent has to be re-applied by the JVM that loads the cache entry, based on its own agent status.
 */
@Requires(
    value = TestExecutionPreconditions.NotEmbeddedExecutor,
    reason = "Relies on a real, cold daemon rebuilding the classloader from the cache entry, which the in-process embedded executor masks"
)
class ConfigurationCacheThirdPartyAgentInstrumentationIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    // The agent rewrites this constant to an equal-length replacement, so the presence of one
    // marker or the other in the output tells us whether the agent's transformation took effect.
    private static final String ORIGINAL_MARKER = "AGENT_UNTOUCHED"
    private static final String PATCHED_MARKER = "AGENT_PATCHED!!"

    static {
        assert ORIGINAL_MARKER.length() == PATCHED_MARKER.length()
    }

    def setup() {
        executer.requireIsolatedDaemons()
    }

    @Issue("https://github.com/gradle/gradle/issues/38619")
    def "third-party agent instrumentation of a project dependency survives restoring from the configuration cache on a cold daemon"() {
        given:
        def agentJar = buildPatchingAgentJar()
        setupProjectWithMarkerProbe()
        def configurationCache = new ConfigurationCacheFixture(this)

        when: "priming the cache"
        withAgent(agentJar)
        configurationCacheRun("probe")

        then: "the agent's transformation is applied on store run"
        configurationCache.assertStateStored()
        outputContains("marker value = $PATCHED_MARKER")

        when: "re-running the build with a cold daemon"
        daemons.killAll()
        withAgent(agentJar)
        configurationCacheRun("probe")

        then: "the cache is loaded and the agent's transformation is reapplied"
        configurationCache.assertStateLoaded()
        loadRanInFreshJvm()
        outputContains("marker value = $PATCHED_MARKER")
    }

    @Issue("https://github.com/gradle/gradle/issues/38619")
    def "third-party agent instrumentation of an external dependency survives restoring from the configuration cache on a cold daemon"() {
        given:
        def agentJar = buildPatchingAgentJar()
        setupProjectWithExternalMarkerProbe()
        def configurationCache = new ConfigurationCacheFixture(this)

        when: "priming the cache"
        withAgent(agentJar)
        configurationCacheRun("probe")

        then: "the agent's transformation is applied on store run"
        configurationCache.assertStateStored()
        outputContains("marker value = $PATCHED_MARKER")

        when: "re-running the build with a cold daemon"
        daemons.killAll()
        withAgent(agentJar)
        configurationCacheRun("probe")

        then: "the cache is loaded and the agent's transformation is reapplied"
        configurationCache.assertStateLoaded()
        loadRanInFreshJvm()
        outputContains("marker value = $PATCHED_MARKER")
    }

    @Issue("https://github.com/gradle/gradle/issues/38619")
    def "third-party agent added after the cache entry was stored transforms buildscript classes on load"() {
        given:
        def agentJar = buildPatchingAgentJar()
        setupProjectWithMarkerProbe()
        def configurationCache = new ConfigurationCacheFixture(this)

        when: "priming the cache without the agent"
        configurationCacheRun("probe")

        then:
        configurationCache.assertStateStored()
        outputContains("marker value = $ORIGINAL_MARKER")

        when: "re-running the build with the agent attached to a cold daemon"
        daemons.killAll()
        withAgent(agentJar)
        configurationCacheRun("probe")

        then: "the cache is loaded and the agent transforms the restored classpath"
        configurationCache.assertStateLoaded()
        loadRanInFreshJvm()
        outputContains("marker value = $PATCHED_MARKER")
    }

    @Issue("https://github.com/gradle/gradle/issues/38619")
    def "third-party agent removed after the cache entry was stored leaves the restored classpath untouched"() {
        given:
        def agentJar = buildPatchingAgentJar()
        setupProjectWithMarkerProbe()
        def configurationCache = new ConfigurationCacheFixture(this)

        when: "priming the cache with the agent"
        withAgent(agentJar)
        configurationCacheRun("probe")

        then:
        configurationCache.assertStateStored()
        outputContains("marker value = $PATCHED_MARKER")

        when: "re-running the build without the agent on a cold daemon"
        daemons.killAll()
        configurationCacheRun("probe")

        then: "the cache is loaded and the pre-instrumented classes are served"
        configurationCache.assertStateLoaded()
        loadRanInFreshJvm()
        outputContains("marker value = $ORIGINAL_MARKER")
    }

    @Issue("https://github.com/gradle/gradle/issues/38619")
    @Requires(
        value = TestExecutionPreconditions.NotNoDaemonExecutor,
        reason = "The last two runs have to be configured on the warm daemon holding the classloader restored by the earlier cache load; without a daemon every run rebuilds the classloader through the resolver instead"
    )
    def "Gradle instrumentation still tracks build logic inputs when composed with a third-party agent"() {
        given:
        def agentJar = buildPatchingAgentJar()
        setupProjectWithMarkerProbe()
        // The property is read at configuration time through a buildscript classpath class,
        // so the read is only tracked if that class carries Gradle instrumentation.
        buildFile """
            println("marker prop = " + test.Marker.readProp())
        """
        def configurationCache = new ConfigurationCacheFixture(this)

        when: "priming the cache"
        withAgent(agentJar)
        configurationCacheRun("probe", "-Dmarker.prop=one")

        then:
        configurationCache.assertStateStored()
        outputContains("marker value = $PATCHED_MARKER")

        when: "re-running the build with a cold daemon"
        daemons.killAll()
        withAgent(agentJar)
        configurationCacheRun("probe", "-Dmarker.prop=one")

        then: "the composed classpath is restored from the cache"
        configurationCache.assertStateLoaded()
        outputContains("marker value = $PATCHED_MARKER")

        when: "the tracked property changes, configuring anew on the warm daemon with the restored classloader"
        withAgent(agentJar)
        configurationCacheRun("probe", "-Dmarker.prop=two")

        then:
        configurationCache.assertStateRecreated {
            systemPropertyChanged("marker.prop")
        }
        daemons.daemons.size() == 2
        outputContains("marker value = $PATCHED_MARKER")

        when: "the property changes again"
        withAgent(agentJar)
        configurationCacheRun("probe", "-Dmarker.prop=three")

        then: "the previous run tracked the read through the composed classloader, so the entry is invalidated again"
        configurationCache.assertStateRecreated {
            systemPropertyChanged("marker.prop")
        }
        outputContains("marker value = $PATCHED_MARKER")
    }

    @Issue("https://github.com/gradle/gradle/issues/38619")
    def "cache entry is gracefully invalidated when the dependency analysis data is removed from the transform cache"() {
        given:
        executer.requireOwnGradleUserHomeDir("deletes files from the shared transform cache otherwise")
        def agentJar = buildPatchingAgentJar()
        setupProjectWithExternalMarkerProbe()
        def configurationCache = new ConfigurationCacheFixture(this)

        when: "priming the cache"
        withAgent(agentJar)
        configurationCacheRun("probe")

        then:
        configurationCache.assertStateStored()
        outputContains("marker value = $PATCHED_MARKER")

        when: "the merged analysis data disappears from the transform cache (e.g. cache cleanup) and a cold daemon reloads the entry"
        daemons.killAll()
        assert !deleteMergedAnalysisFiles().isEmpty()
        withAgent(agentJar)
        configurationCacheRun("probe")

        then: "the entry is invalidated instead of failing or silently dropping the instrumentation"
        outputContains("configuration cache cannot be reused because")
        outputContains("marker value = $PATCHED_MARKER")
    }

    private List<File> deleteMergedAnalysisFiles() {
        def deleted = []
        new File(executer.gradleUserHomeDir, "caches").eachFileRecurse(FileType.FILES) {
            if (it.name == "instrumentation-dependencies.bin" && it.parentFile.name == "merge") {
                assert it.delete()
                deleted << it
            }
        }
        return deleted
    }

    private DaemonLogsAnalyzer getDaemons() {
        new DaemonLogsAnalyzer(executer.daemonBaseDir)
    }

    /**
     * Checks that the runs before and after {@code daemons.killAll()} used distinct daemons, so the
     * loading run reconstructed the buildscript classloader from the cache entry instead of reusing
     * it from memory.
     */
    private void loadRanInFreshJvm() {
        // The no-daemon executor starts a fresh JVM for every run, so there the check
        // is unnecessary (and daemon logs are not written at all).
        if (!GradleContextualExecuter.noDaemon) {
            assert daemons.daemons.size() == 2
        }
    }

    private void withAgent(File agentJar) {
        executer.withBuildJvmOpts("-javaagent:${agentJar.absolutePath}")
    }

    private void setupProjectWithMarkerProbe() {
        // A buildSrc class is a project dependency on the buildscript classpath, so it goes through
        // the instrumenting resolver and the compose step.
        file("buildSrc/src/main/java/test/Marker.java") << markerClass()
        // The value is read from a task action, not at configuration time, so it is observable on a
        // cache load where configuration is skipped and only task actions run.
        buildFile << markerProbeTask()
    }

    private void setupProjectWithExternalMarkerProbe() {
        def module = mavenRepo.module("test", "marker", "1.0").publish()
        def builder = artifactBuilder()
        builder.sourceFile("test/Marker.java") << markerClass()
        builder.buildJar(module.artifactFile)
        buildFile """
            buildscript {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                dependencies {
                    classpath "test:marker:1.0"
                }
            }
        """ + markerProbeTask()
    }

    private String markerClass() {
        javaSnippet """
            package test;

            public class Marker {
                public static String value() {
                    return "$ORIGINAL_MARKER";
                }

                public static String readProp() {
                    return System.getProperty("marker.prop", "unset");
                }
            }
        """
    }

    private String markerProbeTask() {
        buildScriptSnippet """
            tasks.register("probe") {
                doLast {
                    println("marker value = " + test.Marker.value())
                }
            }
        """
    }

    private File buildPatchingAgentJar() {
        def builder = artifactBuilder()
        builder.sourceFile("PatchingAgent.java") << javaSnippet("""
            import java.lang.instrument.ClassFileTransformer;
            import java.lang.instrument.Instrumentation;
            import java.nio.charset.StandardCharsets;
            import java.security.ProtectionDomain;

            public class PatchingAgent {
                // Equal length keeps the constant-pool entry valid without any offset fix-ups, so a
                // plain byte-level search-and-replace is enough. Strings are encoded in UTF-8 there.
                private static final byte[] FROM = "$ORIGINAL_MARKER".getBytes(StandardCharsets.UTF_8);
                private static final byte[] TO = "$PATCHED_MARKER".getBytes(StandardCharsets.UTF_8);

                public static void premain(String args, Instrumentation inst) {
                    inst.addTransformer(new ClassFileTransformer() {
                        @Override
                        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                            if (!"test/Marker".equals(className)) {
                                return null;
                            }
                            return patch(classfileBuffer);
                        }
                    });
                }

                private static byte[] patch(byte[] buffer) {
                    outer:
                    for (int i = 0; i + FROM.length <= buffer.length; i++) {
                        for (int j = 0; j < FROM.length; j++) {
                            if (buffer[i + j] != FROM[j]) {
                                continue outer;
                            }
                        }
                        // Got a match starting at buffer[i].
                        byte[] patched = buffer.clone();
                        System.arraycopy(TO, 0, patched, i, TO.length);
                        return patched;
                    }
                    return null;
                }
            }
        """)
        builder.manifestAttributes("Premain-Class": "PatchingAgent")
        def agentJar = file("patching-agent.jar")
        builder.buildJar(agentJar)
        return agentJar
    }
}
