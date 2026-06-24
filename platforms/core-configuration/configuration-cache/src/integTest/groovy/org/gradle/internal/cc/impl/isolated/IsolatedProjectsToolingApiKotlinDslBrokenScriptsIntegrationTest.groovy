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

package org.gradle.internal.cc.impl.isolated

import org.gradle.integtests.fixtures.build.KotlinDslTestProjectInitiation
import org.gradle.kotlin.dsl.tooling.fixtures.FetchKotlinDslScriptsModelForAllBuilds
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

import static org.gradle.kotlin.dsl.tooling.fixtures.KotlinDslModelChecker.checkBuildTreeScriptsModels
import static org.gradle.kotlin.dsl.tooling.fixtures.KotlinDslModelChecker.checkKotlinDslScriptsModel
import static org.gradle.kotlin.dsl.tooling.fixtures.KotlinDslModelChecker.checkScriptModelEditorReportsArePositioned

class IsolatedProjectsToolingApiKotlinDslBrokenScriptsIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest implements KotlinDslTestProjectInitiation {

    def "can fetch KotlinDslScripts model contains editor reports for #scriptError-broken #scriptKind script in lenient mode"() {
        withSettings("""
            ${scriptError(scriptKind, ScriptKind.SETTINGS, scriptError)}
        """)
        withBuildScript(scriptError(scriptKind, ScriptKind.BUILD, scriptError))
        if (scriptKind == ScriptKind.INIT) {
            def initScript = file("init.gradle.kts")
            initScript.text = scriptError(scriptKind, ScriptKind.INIT, scriptError)
            // The executer is reset() after each run, which clears its init scripts.
            // Use beforeExecute so the --init-script arg is re-applied before each fetch.
            executer.beforeExecute { it.usingInitScript(initScript) }
        }

        when:
        def originalModel = fetchScriptsModelLeniently()

        then:
        fixture.assertNoConfigurationCache()
        // Sanity check: vintage model actually carries editor reports for the broken script
        originalModel.scriptModels.values().any { !it.editorReports.isEmpty() }

        when:
        withIsolatedProjects()
        def model = fetchScriptsModelLeniently()

        then:
        checkKotlinDslScriptsModel(model, originalModel)

        where:
        [scriptKind, scriptError] << [ScriptKind.values() as List, ScriptError.values() as List].combinations()
        // A settings *compile* error is intentionally excluded: without a compilable settings script the
        // build can't determine project structure, so no model is produced at all (the fetch throws) —
        // unlike a settings *runtime* error, which lenient mode tolerates, and unlike build/init compile
        // errors, which degrade gracefully.
            .findAll { it != [ScriptKind.SETTINGS, ScriptError.COMPILE] }
    }

    def "can fetch KotlinDslScripts model honors subproject-local locationAwareEditorHints for runtime-broken script in lenient mode"() {
        withSettings("""
            include("a")
        """)
        withBuildScript(ScriptError.RUNTIME.snippet)
        withBuildScriptIn("a", ScriptError.RUNTIME.snippet)
        file("a/gradle.properties") << "org.gradle.kotlin.dsl.internal.locationAwareEditorHints=true"

        when:
        def originalModel = fetchScriptsModelLeniently()

        then:
        fixture.assertNoConfigurationCache()
        // Sanity check: vintage produces a positioned editor report for :a/build.gradle.kts
        checkScriptModelEditorReportsArePositioned(originalModel.scriptModels, "a${File.separator}build.gradle.kts")

        when:
        withIsolatedProjects()
        def model = fetchScriptsModelLeniently()

        then:
        checkKotlinDslScriptsModel(model, originalModel)
    }

    def "can fetch KotlinDslScripts model honors root-level locationAwareEditorHints for runtime-broken #scriptKinds script in lenient mode"() {
        file("gradle.properties") << "org.gradle.kotlin.dsl.internal.locationAwareEditorHints=true"
        withSettings("""
            ${scriptError(scriptKinds, ScriptKind.SETTINGS, ScriptError.RUNTIME)}
        """)
        withBuildScript(scriptError(scriptKinds, ScriptKind.BUILD, ScriptError.RUNTIME))
        if (scriptKinds.contains(ScriptKind.INIT)) {
            def initScript = file("init.gradle.kts")
            initScript.text = scriptError(scriptKinds, ScriptKind.INIT, ScriptError.RUNTIME)
            executer.beforeExecute { it.usingInitScript(initScript) }
        }

        when:
        def originalModel = fetchScriptsModelLeniently()

        then:
        fixture.assertNoConfigurationCache()
        // Sanity check: vintage produces a positioned editor report for the broken scripts
        scriptKinds.forEach {
            checkScriptModelEditorReportsArePositioned(originalModel.scriptModels, it.scriptFileName)
        }

        when:
        withIsolatedProjects()
        def model = fetchScriptsModelLeniently()

        then:
        checkKotlinDslScriptsModel(model, originalModel)

        where:
        scriptKinds << (ScriptKind.values() as List).subsequences()
    }

    def "can fetch KotlinDslScripts model with positioned editor reports for runtime-broken settings and build scripts across composite build in lenient mode"() {
        file("gradle.properties") << "org.gradle.kotlin.dsl.internal.locationAwareEditorHints=true"
        file("included/gradle.properties") << "org.gradle.kotlin.dsl.internal.locationAwareEditorHints=true"

        withSettingsIn("included", """
            include("a")
            ${ScriptError.RUNTIME.snippet}
        """)
        withBuildScriptIn("included")
        withBuildScriptIn("included/a", ScriptError.RUNTIME.snippet)

        withSettings("""
            includeBuild("included")
            rootProject.name = "root"
            include("a")
            ${ScriptError.RUNTIME.snippet}
        """)
        withBuildScript()
        withBuildScriptIn("a", ScriptError.RUNTIME.snippet)

        when:
        def originalModel = fetchBuildTreeScriptsModelsLeniently()

        then:
        fixture.assertNoConfigurationCache()
        // Sanity check: vintage produces positioned reports for each broken script in each build.
        checkScriptModelEditorReportsArePositioned(originalModel[":"].scriptModels, "settings.gradle.kts")
        checkScriptModelEditorReportsArePositioned(originalModel[":"].scriptModels, "a${File.separator}build.gradle.kts".toString())
        checkScriptModelEditorReportsArePositioned(originalModel[":included"].scriptModels, "settings.gradle.kts")
        checkScriptModelEditorReportsArePositioned(originalModel[":included"].scriptModels, "a${File.separator}build.gradle.kts".toString())

        when:
        withIsolatedProjects()
        def ipModel = fetchBuildTreeScriptsModelsLeniently()

        then:
        checkBuildTreeScriptsModels(ipModel, originalModel)
    }

    def "can fetch KotlinDslScripts model with editor reports for compile-broken build scripts across composite build in lenient mode"() {
        // Settings scripts are left valid on purpose: a settings *compile* error is a hard failure that
        // prevents the build from producing any model. Build-script compile errors degrade gracefully in
        // lenient mode and surface as a ScriptCompilationException whose 'location' embeds the compiler's
        // temp-copy path (normalized by the model checker so IP and non-IP compare equal).
        withSettingsIn("included", """
            include("a")
        """)
        withBuildScriptIn("included/a", ScriptError.COMPILE.snippet)

        withSettings("""
            includeBuild("included")
        """)
        withBuildScript(ScriptError.COMPILE.snippet)

        when:
        def originalModel = fetchBuildTreeScriptsModelsLeniently()

        then:
        fixture.assertNoConfigurationCache()
        // Sanity check: vintage carries the compile-error exception for the broken build scripts in each build.
        originalModel[":"].scriptModels.values().any { !it.exceptions.isEmpty() }
        originalModel[":included"].scriptModels.values().any { !it.exceptions.isEmpty() }

        when:
        withIsolatedProjects()
        def ipModel = fetchBuildTreeScriptsModelsLeniently()

        then:
        checkBuildTreeScriptsModels(ipModel, originalModel)
    }

    private KotlinDslScriptsModel fetchScriptsModelLeniently() {
        toolingApiExecutor.withToolingApiJvmArgs("-Dorg.gradle.kotlin.dsl.provider.mode=classpath")
        return fetchModel(KotlinDslScriptsModel)
    }

    private Map<String, KotlinDslScriptsModel> fetchBuildTreeScriptsModelsLeniently() {
        return runBuildAction(new FetchKotlinDslScriptsModelForAllBuilds()) {
            addJvmArguments("-Dorg.gradle.kotlin.dsl.provider.mode=classpath")
        }
    }

    // Returns the error snippet for 'target' when it is among the broken kinds, otherwise an empty string —
    // used to inject broken scripts of the requested kinds into an otherwise valid build.
    private static String scriptError(Collection<ScriptKind> brokenKinds, ScriptKind target, ScriptError error) {
        target in brokenKinds ? error.snippet : ""
    }

    private static String scriptError(ScriptKind brokenKind, ScriptKind target, ScriptError error) {
        scriptError([brokenKind], target, error)
    }

    private enum ScriptKind {
        BUILD("build.gradle.kts"),
        SETTINGS("settings.gradle.kts"),
        INIT("init.gradle.kts")

        final String scriptFileName

        ScriptKind(String scriptFileName) {
            this.scriptFileName = scriptFileName
        }
    }

    private enum ScriptError {
        // The script compiles but throws during execution; in lenient (classpath) mode the model builder
        // continues and surfaces a LocationAwareException pointing at the real script (eligible for a
        // positioned editor report).
        RUNTIME('error("fail")'),
        // The script fails to compile; the failure is a ScriptCompilationException, which is filtered out
        // of positioned reports and only yields the generic "Build configuration failed" warning. Its
        // message embeds the compiler's temp copy path (normalized by the model checker).
        COMPILE('val x: Int = "not an int"')

        final String snippet

        ScriptError(String snippet) {
            this.snippet = snippet
        }
    }
}
