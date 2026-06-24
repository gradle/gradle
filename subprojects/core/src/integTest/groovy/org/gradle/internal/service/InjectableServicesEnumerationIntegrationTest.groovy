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

package org.gradle.internal.service

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

/**
 * Demonstrates programmatically listing the services injectable from a project's service registry, via
 * {@link ServiceRegistryIntrospection#getAllServiceTypes()}. Produces three views to compare:
 * <ul>
 *     <li>{@code INJECTABLE_SERVICE} - heuristic "public": {@code org.gradle.*} with no {@code internal} package segment.</li>
 *     <li>{@code ALL_SERVICE} - everything under {@code org.gradle.*}, including internal services.</li>
 *     <li>{@code PUBLIC_API_SERVICE} - only types matching the authoritative Gradle public API definition
 *         (PublicApi.kt / PublicKotlinDslApi.kt includes minus excludes).</li>
 * </ul>
 */
@Issue("https://github.com/gradle/gradle/issues/38028")
class InjectableServicesEnumerationIntegrationTest extends AbstractIntegrationSpec {

    def "can list injectable services as heuristic-public, all (incl. internal), and strict public-API"() {
        given:
        buildFile """
            // Enumerate at configuration time and capture only Strings, so the task stays
            // configuration-cache compatible (no live service/registry held by the task).
            def names = (project.services as ${ServiceRegistryIntrospection.name})
                .allServiceTypes
                .collect { it.name }
                .unique()
                .sort()

            // Authoritative Gradle public API definition, mirrored from
            // build-logic-commons/basics/src/main/kotlin/gradlebuild/basics/PublicApi.kt and PublicKotlinDslApi.kt.
            // These are Ant-style path globs matched against the FQCN's slash-path.
            def apiIncludes = [
                "org/gradle/*", "org/gradle/api/**", "org/gradle/authentication/**", "org/gradle/build/**",
                "org/gradle/buildconfiguration/**", "org/gradle/buildinit/**", "org/gradle/caching/**",
                "org/gradle/concurrent/**", "org/gradle/deployment/**", "org/gradle/external/javadoc/**",
                "org/gradle/ide/**", "org/gradle/ivy/**", "org/gradle/jvm/**", "org/gradle/language/**",
                "org/gradle/maven/**", "org/gradle/nativeplatform/**", "org/gradle/normalization/**",
                "org/gradle/platform/**", "org/gradle/plugin/devel/**", "org/gradle/plugin/use/*",
                "org/gradle/plugin/management/*", "org/gradle/plugins/**", "org/gradle/process/**",
                "org/gradle/testfixtures/**", "org/gradle/testing/jacoco/**", "org/gradle/tooling/**",
                "org/gradle/swiftpm/**", "org/gradle/model/**", "org/gradle/testkit/**", "org/gradle/testing/**",
                "org/gradle/vcs/**", "org/gradle/work/**", "org/gradle/workers/**", "org/gradle/util/**",
            ]
            def dslIncludes = ["org/gradle/kotlin/dsl/*", "org/gradle/kotlin/dsl/precompile/*"]
            // PublicApi.excludes; PublicKotlinDslApi.excludes only filters synthetic inlined-lambda classes,
            // which are never registered as services, so it is omitted here.
            def excludes = ["**/internal/**"]

            def antToRegex = { String glob ->
                def out = new StringBuilder()
                int i = 0
                while (i < glob.length()) {
                    if (glob.startsWith("**", i)) { out.append(".*"); i += 2 }
                    else if (glob.charAt(i) == ("*" as char)) { out.append("[^/]*"); i += 1 }
                    else { out.append(glob.charAt(i)); i += 1 }
                }
                out.toString()
            }
            def includeRe = (apiIncludes + dslIncludes).collect { antToRegex(it) }
            def excludeRe = excludes.collect { antToRegex(it) }
            def isPublicApi = { String fqcn ->
                def path = fqcn.replace(".", "/")
                includeRe.any { path ==~ it } && !excludeRe.any { path ==~ it }
            }

            def gradleServices    = names.findAll { it.startsWith("org.gradle.") }
            def publicHeuristic   = gradleServices.findAll { !it.contains(".internal.") }
            def allServices       = gradleServices              // includes internal
            def publicApiServices = names.findAll { isPublicApi(it) }

            tasks.register("listInjectableServices") {
                doLast {
                    publicHeuristic.each { println "INJECTABLE_SERVICE: \$it" }
                    println "INJECTABLE_SERVICE_COUNT: \${publicHeuristic.size()}"
                    allServices.each { println "ALL_SERVICE: \$it" }
                    println "ALL_SERVICE_COUNT: \${allServices.size()}"
                    publicApiServices.each { println "PUBLIC_API_SERVICE: \$it" }
                    println "PUBLIC_API_SERVICE_COUNT: \${publicApiServices.size()}"
                }
            }
        """

        when:
        run "listInjectableServices"

        then:
        def lines = output.readLines()
        def extract = { String prefix ->
            lines.findAll { it.startsWith(prefix) }.collect { it.substring(prefix.length()) }
        }
        def heuristic = extract("INJECTABLE_SERVICE: ")
        def all = extract("ALL_SERVICE: ")
        def api = extract("PUBLIC_API_SERVICE: ")

        and: "documented public services appear in the heuristic and strict public-API lists"
        ["org.gradle.api.model.ObjectFactory",
         "org.gradle.api.provider.ProviderFactory",
         "org.gradle.api.file.FileSystemOperations",
         "org.gradle.api.file.ProjectLayout"].each {
            assert heuristic.contains(it)
            assert api.contains(it)
        }

        and: "the all-services list includes internal services that the other lists omit"
        all.any { it.contains(".internal.") }
        heuristic.every { !it.contains(".internal.") }
        api.every { !it.contains(".internal.") }

        and: "the strict public-API list excludes non-public packages (e.g. launcher/daemon, execution.plan) that the heuristic lets through"
        !api.contains("org.gradle.launcher.daemon.server.Daemon")
        !api.any { it.startsWith("org.gradle.execution.plan.") }
        heuristic.contains("org.gradle.launcher.daemon.server.Daemon")

        and: "lists narrow from all -> heuristic-public -> strict public-API, and public-API is a subset of the heuristic"
        all.size() > heuristic.size()
        heuristic.size() > api.size()
        api.every { heuristic.contains(it) }
    }

    def "can determine which services actually resolve for injection without failure"() {
        given:
        buildFile """
            // Injection ultimately does serviceLookup.find(type): it returns the instance, returns null
            // ("no service of type X"), or throws (e.g. ambiguous - multiple services match a supertype).
            // Probe each candidate the same way and classify. Done at configuration time; only Strings are
            // captured into the task, so it stays configuration-cache compatible.
            def intro = project.services as ${ServiceRegistryIntrospection.name}
            def candidates = intro.allServiceTypes
                .findAll { it.name.startsWith("org.gradle.") && !it.name.contains(".internal.") }
                .sort { it.name }

            def ok = []
            def fails = []
            candidates.each { c ->
                try {
                    def svc = project.services.find(c)
                    if (svc != null) {
                        ok << c.name
                    } else {
                        fails << (c.name + " | null (no service of this type)")
                    }
                } catch (Throwable t) {
                    fails << (c.name + " | " + t.getClass().simpleName)
                }
            }

            tasks.register("probeInjectableServices") {
                doLast {
                    ok.each { println "INJECTS_OK: \$it" }
                    println "INJECTS_OK_COUNT: \${ok.size()}"
                    fails.each { println "INJECTS_FAILS: \$it" }
                    println "INJECTS_FAILS_COUNT: \${fails.size()}"
                }
            }
        """

        when:
        run "probeInjectableServices"

        then:
        def lines = output.readLines()
        def ok = lines.findAll { it.startsWith("INJECTS_OK: ") }.collect { it.substring("INJECTS_OK: ".length()) }
        def fails = lines.findAll { it.startsWith("INJECTS_FAILS: ") }.collect { it.substring("INJECTS_FAILS: ".length()) }

        and: "documented injectable services resolve cleanly"
        ["org.gradle.api.model.ObjectFactory",
         "org.gradle.api.provider.ProviderFactory",
         "org.gradle.api.file.FileSystemOperations",
         "org.gradle.api.file.ProjectLayout"].each { assert ok.contains(it) }

        and: "not everything in the enumerated list resolves - some fail (e.g. ambiguous supertypes that match multiple services)"
        !fails.isEmpty()
        ok.every { it.startsWith("org.gradle.") && !it.contains(".internal.") }

        and: "ubiquitous supertypes like Action are ambiguous (multiple services match) and so are NOT injectable"
        !ok.contains("org.gradle.api.Action")
        fails.any { it.startsWith("org.gradle.api.Action |") }
    }

    def "the injectable service set is the same under vintage, configuration cache, and isolated projects"() {
        given:
        buildFile """
            def names = (project.services as ${ServiceRegistryIntrospection.name})
                .allServiceTypes
                .collect { it.name }
                .findAll { it.startsWith("org.gradle.") && !it.contains(".internal.") }
                .unique()
                .sort()
            tasks.register("listInjectableServices") {
                doLast { names.each { println "INJECTABLE_SERVICE: \$it" } }
            }
        """
        def servicesFrom = { result ->
            result.output.readLines()
                .findAll { it.startsWith("INJECTABLE_SERVICE: ") }
                .collect { it.substring("INJECTABLE_SERVICE: ".length()) } as Set
        }

        when:
        def vintage = servicesFrom(run("listInjectableServices"))
        def configCache = servicesFrom(run("-Dorg.gradle.configuration-cache=true", "listInjectableServices"))
        def isolatedProjects = servicesFrom(run("-Dorg.gradle.unsafe.isolated-projects=true", "listInjectableServices"))

        then: "the publicly-injectable (org.gradle.api.*) surface is identical across all three modes"
        def apiOnly = { Set s -> s.findAll { it.startsWith("org.gradle.api.") } as Set }
        apiOnly(vintage) == apiOnly(configCache)
        apiOnly(vintage) == apiOnly(isolatedProjects)

        and: "configuration cache and isolated projects only ever ADD services, never remove them"
        (vintage - configCache).isEmpty()
        (vintage - isolatedProjects).isEmpty()

        and: "and the only additions are mode-specific infrastructure services, not public API"
        // empirically CC/IP register e.g. org.gradle.initialization.ClassLoaderScopeRegistryListener
        (configCache - vintage).every { !it.startsWith("org.gradle.api.") }
        (isolatedProjects - vintage).every { !it.startsWith("org.gradle.api.") }
    }

    def "can list the project-scoped public-API services (own scope vs inherited from build/global)"() {
        given:
        buildFile """
            def intro = project.services as ${ServiceRegistryIntrospection.name}
            def ownTypes = intro.ownServiceTypes                                 // project-scoped only (Class objects)
            def allTypes = intro.allServiceTypes                                 // project + all ancestors (Class objects)

            // Public API definition mirrored from PublicApi.kt / PublicKotlinDslApi.kt (Ant-style path globs).
            def apiIncludes = [
                "org/gradle/*", "org/gradle/api/**", "org/gradle/authentication/**", "org/gradle/build/**",
                "org/gradle/buildconfiguration/**", "org/gradle/buildinit/**", "org/gradle/caching/**",
                "org/gradle/concurrent/**", "org/gradle/deployment/**", "org/gradle/external/javadoc/**",
                "org/gradle/ide/**", "org/gradle/ivy/**", "org/gradle/jvm/**", "org/gradle/language/**",
                "org/gradle/maven/**", "org/gradle/nativeplatform/**", "org/gradle/normalization/**",
                "org/gradle/platform/**", "org/gradle/plugin/devel/**", "org/gradle/plugin/use/*",
                "org/gradle/plugin/management/*", "org/gradle/plugins/**", "org/gradle/process/**",
                "org/gradle/testfixtures/**", "org/gradle/testing/jacoco/**", "org/gradle/tooling/**",
                "org/gradle/swiftpm/**", "org/gradle/model/**", "org/gradle/testkit/**", "org/gradle/testing/**",
                "org/gradle/vcs/**", "org/gradle/work/**", "org/gradle/workers/**", "org/gradle/util/**",
                "org/gradle/kotlin/dsl/*", "org/gradle/kotlin/dsl/precompile/*",
            ]
            def excludes = ["**/internal/**"]
            def antToRegex = { String glob ->
                def out = new StringBuilder(); int i = 0
                while (i < glob.length()) {
                    if (glob.startsWith("**", i)) { out.append(".*"); i += 2 }
                    else if (glob.charAt(i) == ("*" as char)) { out.append("[^/]*"); i += 1 }
                    else { out.append(glob.charAt(i)); i += 1 }
                }
                out.toString()
            }
            def incRe = apiIncludes.collect { antToRegex(it) }
            def excRe = excludes.collect { antToRegex(it) }
            def isPublicApi = { String fqcn ->
                def path = fqcn.replace(".", "/")
                incRe.any { path ==~ it } && !excRe.any { path ==~ it }
            }

            // Project-scoped = registered in this (project) registry's own services.
            // Inherited (non-project) = resolvable only via the parent chain (build/buildTree/.../global).
            def projectScopedTypes = ownTypes.findAll { isPublicApi(it.name) }.sort { it.name }
            def inheritedTypes = (allTypes - ownTypes).findAll { isPublicApi(it.name) }.sort { it.name }
            def projectScopedPublicApi = projectScopedTypes.collect { it.name }
            def inheritedPublicApi = inheritedTypes.collect { it.name }

            // "Injectable" = actually resolves via find() (the same call @Inject makes). This drops types
            // that match MULTIPLE services (e.g. Action) and so fail with a not-unique ServiceLookupException.
            // Maps each injectable service type to the concrete class of the resolved instance.
            def probe = { types ->
                def out = new TreeMap()
                types.each { c ->
                    try { def svc = project.services.find(c); if (svc != null) out[c.name] = svc.getClass().name } catch (Throwable ignored) { }
                }
                out
            }
            def projectScopedInjectable = probe(projectScopedTypes)
            def inheritedInjectable = probe(inheritedTypes)

            tasks.register("listProjectScopedServices") {
                doLast {
                    projectScopedPublicApi.each { println "PROJECT_SCOPED_PUBLIC_API: \$it" }
                    println "PROJECT_SCOPED_PUBLIC_API_COUNT: \${projectScopedPublicApi.size()}"
                    inheritedPublicApi.each { println "INHERITED_PUBLIC_API: \$it" }
                    println "INHERITED_PUBLIC_API_COUNT: \${inheritedPublicApi.size()}"
                    projectScopedInjectable.each { k, v -> println "PROJECT_SCOPED_INJECTABLE: \$k -> \$v" }
                    println "PROJECT_SCOPED_INJECTABLE_COUNT: \${projectScopedInjectable.size()}"
                    inheritedInjectable.each { k, v -> println "INHERITED_INJECTABLE: \$k -> \$v" }
                    println "INHERITED_INJECTABLE_COUNT: \${inheritedInjectable.size()}"
                }
            }
        """

        when:
        run "listProjectScopedServices"

        then:
        def lines = output.readLines()
        def extract = { String prefix -> lines.findAll { it.startsWith(prefix) }.collect { it.substring(prefix.length()) } }
        def projectScoped = extract("PROJECT_SCOPED_PUBLIC_API: ")
        def inherited = extract("INHERITED_PUBLIC_API: ")
        // entries are "type -> implClass"; take the type for the assertions below
        def injectable = extract("PROJECT_SCOPED_INJECTABLE: ").collect { it.split(" -> ", 2)[0] }
        def inheritedInjectable = extract("INHERITED_INJECTABLE: ").collect { it.split(" -> ", 2)[0] }

        and: "Project-scoped public services are present in the project-scoped list"
        projectScoped.contains("org.gradle.api.file.ProjectLayout")
        projectScoped.contains("org.gradle.workers.WorkerExecutor")
        projectScoped.contains("org.gradle.api.model.ObjectFactory")

        and: "services scoped to build/parent are inherited, NOT project-scoped"
        !projectScoped.contains("org.gradle.api.provider.ProviderFactory")   // Build scope
        !projectScoped.contains("org.gradle.api.invocation.Gradle")          // Build scope
        inherited.contains("org.gradle.api.provider.ProviderFactory")

        and: "the two sets are disjoint and both non-trivial"
        projectScoped.intersect(inherited).isEmpty()
        projectScoped.size() >= 3

        and: "the definitive list (project-scoped AND public-API AND actually injectable) keeps the real targets"
        injectable.contains("org.gradle.api.file.ProjectLayout")
        injectable.contains("org.gradle.workers.WorkerExecutor")
        injectable.contains("org.gradle.api.model.ObjectFactory")
        injectable.contains("org.gradle.api.artifacts.dsl.DependencyFactory")

        and: "and drops the ambiguous supertypes that match multiple project services"
        injectable.every { projectScoped.contains(it) }            // subset of project-scoped public-API
        !injectable.contains("org.gradle.api.DomainObjectCollection")
        !injectable.contains("org.gradle.api.NamedDomainObjectContainer")
        injectable.size() < projectScoped.size()                  // some were dropped

        and: "the inherited (non-project) public-API services that are actually injectable"
        inheritedInjectable.every { inherited.contains(it) }       // subset of inherited public-API
        inheritedInjectable.contains("org.gradle.api.provider.ProviderFactory")   // Build scope, uniquely resolvable
        inheritedInjectable.contains("org.gradle.api.problems.Problems")          // BuildTree scope
        // Action is inherited but matches MULTIPLE services, so it is NOT uniquely injectable
        inherited.contains("org.gradle.api.Action")
        !inheritedInjectable.contains("org.gradle.api.Action")
        inheritedInjectable.size() < inherited.size()             // ambiguous ones dropped
    }

    def "verifies the scope classification by instance identity across two projects"() {
        given:
        file("a").createDir()
        file("b").createDir()
        settingsFile << "include('a', 'b')\n"
        buildFile """
            // For each candidate, classify via the introspection (own = project-scoped) and capture the
            // identity of the resolved instance. Run in two projects: a project-scoped service yields a
            // DISTINCT instance per project; an inherited (build/global) service yields the SAME shared instance.
            subprojects { sp ->
                def intro = sp.services as ${ServiceRegistryIntrospection.name}
                def ownNames = intro.ownServiceTypes.collect { it.name } as Set
                def candidates = [
                    "org.gradle.api.model.ObjectFactory", "org.gradle.api.file.ProjectLayout",
                    "org.gradle.workers.WorkerExecutor", "org.gradle.api.tasks.TaskContainer",
                    "org.gradle.api.artifacts.dsl.RepositoryHandler", "org.gradle.api.artifacts.dsl.DependencyHandler",
                    "org.gradle.api.invocation.Gradle", "org.gradle.api.provider.ProviderFactory",
                    "org.gradle.api.problems.Problems", "org.gradle.api.services.BuildServiceRegistry",
                    "org.gradle.api.configuration.BuildFeatures",
                ]
                def rows = []
                candidates.each { n ->
                    def scope = ownNames.contains(n) ? "OWN" : "INHERITED"
                    def id = "null"
                    try { def svc = sp.services.find(Class.forName(n)); if (svc != null) id = System.identityHashCode(svc).toString() }
                    catch (Throwable ignored) { }
                    rows << "SCOPE_IDENTITY: \${sp.path}|\$n|\$scope|\$id"
                }
                sp.tasks.register("scopeIdentity") { doLast { rows.each { println it } } }
            }
        """

        when:
        run ":a:scopeIdentity", ":b:scopeIdentity"

        then:
        def rows = output.readLines()
            .findAll { it.startsWith("SCOPE_IDENTITY: ") }
            .collect { it.substring("SCOPE_IDENTITY: ".length()).split("\\|") }   // [path, name, scope, id]
        def scopeOf = { n -> rows.find { it[1] == n && it[0] == ":a" }[2] }

        and: "the introspection classification matches known scopes"
        scopeOf("org.gradle.api.model.ObjectFactory") == "OWN"
        scopeOf("org.gradle.api.file.ProjectLayout") == "OWN"
        scopeOf("org.gradle.api.tasks.TaskContainer") == "OWN"
        scopeOf("org.gradle.api.provider.ProviderFactory") == "INHERITED"
        scopeOf("org.gradle.api.invocation.Gradle") == "INHERITED"
        scopeOf("org.gradle.api.problems.Problems") == "INHERITED"

        and: "identity proves it: project-scoped => distinct per project; inherited => the same shared instance"
        rows.groupBy { it[1] }.each { name, list ->
            def a = list.find { it[0] == ":a" }; def b = list.find { it[0] == ":b" }
            assert a && b && a[2] == b[2]                          // present in both, same classification
            if (a[3] != "null" && b[3] != "null") {
                if (a[2] == "OWN") {
                    assert a[3] != b[3] : "project-scoped ${name} should be a distinct instance per project"
                } else {
                    assert a[3] == b[3] : "inherited ${name} should be one shared instance across projects"
                }
            }
        }
    }

    def "screens project-scoped public-API services for references to inherited (build/global) services"() {
        given:
        buildFile """
            import java.lang.reflect.Modifier
            def intro = project.services as ${ServiceRegistryIntrospection.name}
            def ownNames = intro.ownServiceTypes.collect { it.name } as Set
            def allNames = intro.allServiceTypes.collect { it.name } as Set

            // public API filter, mirrored from PublicApi.kt / PublicKotlinDslApi.kt
            def apiIncludes = [
                "org/gradle/*", "org/gradle/api/**", "org/gradle/authentication/**", "org/gradle/build/**",
                "org/gradle/buildconfiguration/**", "org/gradle/buildinit/**", "org/gradle/caching/**",
                "org/gradle/concurrent/**", "org/gradle/deployment/**", "org/gradle/external/javadoc/**",
                "org/gradle/ide/**", "org/gradle/ivy/**", "org/gradle/jvm/**", "org/gradle/language/**",
                "org/gradle/maven/**", "org/gradle/nativeplatform/**", "org/gradle/normalization/**",
                "org/gradle/platform/**", "org/gradle/plugin/devel/**", "org/gradle/plugin/use/*",
                "org/gradle/plugin/management/*", "org/gradle/plugins/**", "org/gradle/process/**",
                "org/gradle/testfixtures/**", "org/gradle/testing/jacoco/**", "org/gradle/tooling/**",
                "org/gradle/swiftpm/**", "org/gradle/model/**", "org/gradle/testkit/**", "org/gradle/testing/**",
                "org/gradle/vcs/**", "org/gradle/work/**", "org/gradle/workers/**", "org/gradle/util/**",
                "org/gradle/kotlin/dsl/*", "org/gradle/kotlin/dsl/precompile/*",
            ]
            def antToRegex = { String glob ->
                def out = new StringBuilder(); int i = 0
                while (i < glob.length()) {
                    if (glob.startsWith("**", i)) { out.append(".*"); i += 2 }
                    else if (glob.charAt(i) == ("*" as char)) { out.append("[^/]*"); i += 1 }
                    else { out.append(glob.charAt(i)); i += 1 }
                }
                out.toString()
            }
            def incRe = apiIncludes.collect { antToRegex(it) }
            def isPublicApi = { String fqcn ->
                def path = fqcn.replace(".", "/")
                incRe.any { path ==~ it } && !path.contains("/internal/")
            }
            // A dependency is "inherited" (shared, build/global) if it is a registered service type that
            // is NOT in the project's own services.
            def classify = { String n -> ownNames.contains(n) ? "OWN" : (allNames.contains(n) ? "INHERITED" : null) }

            def projectScoped = intro.ownServiceTypes.findAll { isPublicApi(it.name) }.sort { it.name }
            def report = []
            projectScoped.each { svcType ->
                def inst
                try { inst = project.services.find(svcType) } catch (Throwable ignored) { return }
                if (inst == null) return
                def seen = [] as Set
                def k = inst.getClass()
                while (k != null && k != Object) {
                    try {
                        k.declaredFields.each { f ->
                            def depName = f.type.name
                            if (classify(depName) == "INHERITED" && seen.add(depName)) {
                                report << (svcType.name + " -> " + depName)
                            }
                        }
                    } catch (Throwable ignored) { }
                    k = k.getSuperclass()
                }
            }
            report = report.sort().unique()

            tasks.register("screenProjectScopedDeps") {
                doLast {
                    report.each { println "PS_REACHES_SHARED: \$it" }
                    println "PS_REACHES_SHARED_COUNT: \${report.size()}"
                    println "PS_REACHES_SHARED_SERVICES: \${report.collect { it.split(' -> ')[0] }.unique().size()}"
                }
            }
        """

        when:
        run "screenProjectScopedDeps"

        then: "the screen produces well-formed 'projectScopedService -> inheritedService' pairs"
        def deps = output.readLines().findAll { it.startsWith("PS_REACHES_SHARED: ") }.collect { it.substring("PS_REACHES_SHARED: ".length()) }
        deps.every { it.contains(" -> ") }

        and: "it is only a screen: some project-scoped services do reference shared services (to be reviewed against the freeze/immutable/sink criterion)"
        // the build script only emits pairs whose right-hand side is classified INHERITED, so a non-empty,
        // well-formed result is the signal; safety of each reference is a per-service judgement.
        !deps.isEmpty()
    }
}
