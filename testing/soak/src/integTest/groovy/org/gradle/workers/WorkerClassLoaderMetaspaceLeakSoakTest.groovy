/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.workers

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.gradle.workers.fixtures.WorkerExecutorFixture
import spock.lang.Issue

/**
 * Soak/acceptance guard for the within-session Worker API Metaspace leak (issue #18313).
 *
 * This is deliberately a soak test, not a per-PR integration test: it is calibration-sensitive (the {@code (N, M, cap)}
 * triple depends on the daemon's Metaspace baseline, which is host/JDK-specific and elastic per JEP-387), and on the
 * pre-fix baseline it exhausts Metaspace slowly (via GC thrashing under the cap) rather than failing instantly.
 */
class WorkerClassLoaderMetaspaceLeakSoakTest extends AbstractIntegrationSpec {

    def fixture = new WorkerExecutorFixture(temporaryFolder)

    def setup() {
        fixture.prepareTaskTypeUsingWorker()
    }

    @Override
    TestFile getBuildFile() {
        return fixture.getBuildFile()
    }

    @Requires(
        value = TestExecutionPreconditions.NotEmbeddedExecutor,
        reason = "needs an isolated daemon with a bounded Metaspace"
    )
    @IntegrationTestTimeout(300)
    @Issue("https://github.com/gradle/gradle/issues/18313")
    def "worker classLoaderIsolation does not leak class metadata across items within a single build session"() {
        given: "calibration knobs"
        // Sized so an UNFIXED daemon's cumulative class metadata (~N*M classes strong-pinned in valuesForThisSession
        // for the whole session, ~200MB) exceeds the cap, while a FIXED daemon collects each per-item ClassValue-backed
        // loader mid-build and its steady state stays a small fraction of it.
        // NOTE: this is a calibration-sensitive soak/acceptance guard -- the exact (N, M, cap) triple depends on the
        // daemon's Metaspace baseline (host/JDK-specific, elastic per JEP-387); if the FIXED run flakes on a
        // higher-baseline host, loosen the cap; if the baseline case stops OOMing, raise N/M.
        int workerItems = 40          // N tasks == N fresh per-item VisitableURLClassLoaders (reuseClassloader=false)
        int classesPerJar = 500       // M distinct (non-trivial) classes each item force-loads into its own loader
        String metaspaceCap = "128m"

        and: "an isolated daemon under a bounded Metaspace cap"
        executer
            .requireDaemon()
            .requireIsolatedDaemons()
            .withStackTraceChecksDisabled()
            .withArgument("--max-workers=1")                            // strictly sequential -> deterministic peak
        // implicit -XX:MaxMetaspaceSize=512m is appended first; this later flag wins (last-flag-wins).
        // Do NOT call useOnlyRequestedJvmOpts() (it would strip -ea and the base heap args).
            .withBuildJvmOpts("-XX:MaxMetaspaceSize=${metaspaceCap}")

        and: "a network-free payload jar of many distinct classes"
        def payloadJar = file("libs/payload.jar")
        def builder = artifactBuilder()
        // Non-trivial classes (fields + many methods) so each carries several KB of class metadata -- this lets the
        // UNFIXED cumulative footprint cross the cap after far fewer loads (i.e. quickly), avoiding a timeout.
        def methods = (0..<20).collect { m -> "public long m${m}(long p) { return f${m % 8} + p + ${m}L; }" }.join("\n")
        (0..<classesPerJar).each { i ->
            builder.sourceFile("gen/Gen${i}.java").text =
                "package gen; public class Gen${i} { long f0,f1,f2,f3,f4,f5,f6,f7;\n${methods}\n}"
        }
        builder.buildJar(payloadJar)

        and: "the work action force-loads every payload class into its own per-item classloader"
        fixture.workActionThatCreatesFiles.action += """
            try {
                ClassLoader cl = getClass().getClassLoader();
                for (int i = 0; i < ${classesPerJar}; i++) {
                    Class.forName("gen.Gen" + i, true, cl);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        """
        fixture.withWorkActionClassInBuildSrc()

        and: "N tasks, each submitting one classLoaderIsolation work item on a fresh loader"
        buildFile << """
            def payload = files('libs/payload.jar')
            def loadTasks = (0..<${workerItems}).collect { n ->
                tasks.register("load\$n", WorkerTask) {
                    isolationMode = 'classLoaderIsolation'
                    additionalClasspath = payload
                }
            }
            tasks.register("loadAll") { dependsOn loadTasks }
        """

        expect: "on FIXED (ClassValue-backed) code the per-item loaders are collected mid-build, so a bounded Metaspace suffices"
        // On UNFIXED code each item's Class is strong-pinned in valuesForThisSession for the whole session; ~N*M
        // metadata copies accumulate under the cap and the daemon exhausts Metaspace
        succeeds("loadAll")

        and: "exactly one private, isolated daemon existed and is still alive/idle (no crash, no leak)"
        def daemons = new DaemonLogsAnalyzer(executer.daemonBaseDir)
        daemons.daemons.size() == 1
        daemons.daemon.assertIdle()
    }
}
