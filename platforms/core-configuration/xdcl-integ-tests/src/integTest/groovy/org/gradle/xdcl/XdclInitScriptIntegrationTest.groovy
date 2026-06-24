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

package org.gradle.xdcl

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

/**
 * Coverage for {@code .gradle.xdcl} init scripts. An init script is a contribution to the build: it
 * brings plugins (via {@code settingsPluginManagement}/{@code settingsPlugins}) and build-level
 * {@code defaults} (layered ABOVE settings defaults — init overrides settings). The contribution is
 * applied to the invoked build whether its settings is {@code settings.gradle.xdcl} (drained by the
 * settings apply) or not (synthesized from an empty settings). The plugin-source build the init
 * pulls in via {@code includedBuilds} is excluded from receiving the contribution.
 *
 * <h3>HANDOFF NOTES (for merging with the defaults work — read before extending)</h3>
 *
 * <p><b>Design summary (what these tests pin):</b>
 * <ul>
 *   <li>Init {@code defaults} are an extra layer on top of settings defaults. Implemented as
 *       {@code DefaultsInput.layer} (settings = layer 0, init = layer 1) in the {@code :eval} module;
 *       {@code DefaultsRegistryBuilder} selects per {@code (scope, property)} by HIGHEST LAYER first,
 *       then the existing specificity rank within that layer, then a same-layer-same-specificity
 *       {@code C-DEFAULT-CONFLICT}. A single-layer (settings-only) build is byte-for-byte unchanged —
 *       this is the main compatibility contract to preserve when merging.</li>
 *   <li>Defaults resolution is <b>per-build-MERGED, not per-file</b>: every init script's + the
 *       settings file's defaults build against the SAME complete registry (settings plugins ∪ all
 *       init scripts' {@code settingsPlugins}). A default resolves if ANY contributor to that build
 *       provides the type. See the conflict test for the consequence.
 *       <br><b>OPEN QUESTION:</b> whether (and how) to resolve an init file's own plugin
 *       dependencies for ISOLATED validation — i.e. validating each init file against just the
 *       plugins IT contributes, so a file is meaningful on its own and so IDE/tooling support works
 *       OUTSIDE the context of a concrete build (where no merged registry exists). Feasible at init
 *       time only for repository-sourced {@code settingsPlugins} (the {@code Gradle} object has no
 *       {@code includeBuild}); the includeBuild-sourced case and the IDE-outside-a-build case are
 *       unsolved. This would be an ADDITIONAL validation pass, not a replacement for the merged
 *       resolution (which still does cross-source conflict detection and projection).</li>
 *   <li>Defaults errors resolve against EACH contributing document's own source map
 *       ({@code XdclProblems.defaultsFailure} + {@code Diagnostics.formatConfigErrorsMultiDoc}),
 *       since a {@code defaults} block may live in the settings file or any init script.</li>
 * </ul>
 *
 * <p><b>Works, but PARTIAL / may need changes:</b>
 * <ul>
 *   <li><b>Init-overrides-settings precedence is an OPEN design decision</b> (see the TODO on the
 *       override test). "default" arguably implies a WEAK baseline a build can override; forcing the
 *       build is policy enforcement and may warrant a separate explicit construct. Flipping this
 *       inverts the layer order — touches {@code DefaultsInput.layer} assignment only, not the
 *       builder algorithm.</li>
 *   <li><b>"Reaches all builds" does not compose for includeBuild-sourced plugins.</b>
 *       {@code settingsPluginManagement.includedBuilds} is a RELATIVE path resolved per receiving
 *       build, so it only works where the dir layout matches (effectively the invoked build). The
 *       repository-sourced path is the one that genuinely composes tree-wide — and is NOT yet tested
 *       (these tests use includeBuild because the integ harness has no plugin repo).</li>
 *   <li><b>Included builds contributed via {@code includedBuilds} must NOT receive the init
 *       contribution.</b> Init scripts run for EVERY build in the tree, so the same init file is
 *       applied to a build it pulls in as a plugin source — which would then try to
 *       {@code includeBuild} that same build again: either re-including an already-included build
 *       (failure) or recursing. A mechanism is REQUIRED to stop the init file's contribution from
 *       being applied to (some of) the included builds. Current mitigation: a {@code BuildTree}-scoped
 *       {@code XdclInitPluginSources} records each build the init names in {@code includedBuilds}, and
 *       a build that finds its own root dir recorded self-excludes (early return in {@code applyInit}).
 *       This is LOAD-BEARING and rests on ordering (the including build's {@code applyInit} runs before
 *       the included build is configured) and on path canonicalization matching — both unverified for
 *       nested composites / symlinked or absolute include paths. Note: only the *contribution* is
 *       excluded today; the init script still otherwise runs in those builds.</li>
 *   <li><b>{@code settingsPlugins} cross-script version conflicts are silent</b> — merged requests
 *       dedupe by id (first wins), unlike defaults which poison. Inconsistent; may want detection.</li>
 *   <li><b>buildSrc</b> is not handled explicitly — it runs init scripts and would receive the
 *       contribution; untested and likely needs the plugin-source exclusion treatment.</li>
 * </ul>
 *
 * <p><b>Deliberately LEFT OUT of v1:</b>
 * <ul>
 *   <li><b>Init-LOCAL plugin application</b> (a {@code Plugin<Gradle>} applied to the Gradle object
 *       by id) — the {@code pluginManagement}/{@code plugins} fields were REMOVED from the init
 *       bootstrap schema. Blocked by classloader visibility: the Build-scoped plugin-marker repo
 *       handler ({@code PluginRepositoryHandlerProvider}) lives only in {@code gradle-plugin-use.jar},
 *       which the provider's runtime classloader can't see. See design doc §7a.</li>
 *   <li><b>Relying on the ecosystem</b> (the {@code relyOnEcosystems}-style declaration covered in
 *       the declarative-init design docs) — not implemented here. Out of scope for this slice.</li>
 * </ul>
 *
 * <p><b>NOT yet covered (gaps to fill when merging):</b> configuration cache ({@code configCacheIntegTest}
 * — the most important missing gate, since init runs at config time and rides Build- + BuildTree-scoped
 * services); repository-sourced {@code settingsPlugins}; contribution reaching a real (non-source)
 * included build; reactions firing from init-contributed plugins; init parse-error (vs evaluation-error)
 * location; cross-specificity layering (init's broad default beating settings' narrow one).
 */
class XdclInitScriptIntegrationTest extends AbstractIntegrationSpec {

    def "init script contributes a plugin and defaults to an xdcl-settings build"() {
        given:
        projectTemplates('org-plugins')
        xdclSettingsFile '''
            settings {
                include ["app"]
            }
        '''
        xdclFile 'app/build.gradle.xdcl', 'application {}'
        xdclInitScript 'init.gradle.xdcl', '''
            initscript {
                settingsPluginManagement { includedBuilds ["org-plugins"] }
                settingsPlugins [ { id "project-templates" } ]
                defaults {
                    application { name "from-init" }
                    for MyComponent { version "9.9.9" }
                }
            }
        '''

        when: 'the plugin-source build (org-plugins) is excluded; app receives the contribution'
        executer.usingInitScript(file('init.gradle.xdcl'))
        succeeds("help")

        then:
        outputContains 'app { name "from-init", version "9.9.9"}'
    }

    def "init script contributes to a build with a non-xdcl settings (synthesized)"() {
        given:
        projectTemplates('org-plugins')
        // Groovy settings — no settings.gradle.xdcl. The init contribution is synthesized as if the
        // settings file were an empty `settings { }`, so the build.gradle.xdcl still resolves.
        file('settings.gradle') << 'include "app"'
        xdclFile 'app/build.gradle.xdcl', 'application {}'
        xdclInitScript 'init.gradle.xdcl', '''
            initscript {
                settingsPluginManagement { includedBuilds ["org-plugins"] }
                settingsPlugins [ { id "project-templates" } ]
                defaults {
                    application { name "from-init" }
                    for MyComponent { version "9.9.9" }
                }
            }
        '''

        when:
        executer.usingInitScript(file('init.gradle.xdcl'))
        succeeds("help")

        then:
        outputContains 'app { name "from-init", version "9.9.9"}'
    }

    // OPEN DESIGN DECISION (pins current behavior, may flip): init defaults currently OVERRIDE
    // settings defaults (init = layer 1 > settings = layer 0). This might instead need init-scoped
    // defaults to be a WEAKER baseline than what the build provides, with a separate explicit
    // construct (e.g. an enforced/policy intent) for the override case. Flipping this changes only
    // the layer assigned to init inputs, not the builder's selection algorithm.
    def "init defaults override settings defaults"() {
        given:
        projectTemplates('org-plugins')
        xdclSettingsFile '''
            settings {
                include ["app"]
                defaults {
                    application { name "from-settings" }
                    for MyComponent { version "0.0.1" }
                }
            }
        '''
        xdclFile 'app/build.gradle.xdcl', 'application {}'
        xdclInitScript 'init.gradle.xdcl', '''
            initscript {
                settingsPluginManagement { includedBuilds ["org-plugins"] }
                settingsPlugins [ { id "project-templates" } ]
                defaults {
                    for MyComponent { version "9.9.9" }
                }
            }
        '''

        when:
        executer.usingInitScript(file('init.gradle.xdcl'))
        succeeds("help")

        then: 'init wins on version (layer 1); settings still supplies the name it alone declares'
        outputContains 'app { name "from-settings", version "9.9.9"}'
    }

    def "non-conflicting defaults from multiple init scripts all apply"() {
        given:
        projectTemplates('org-plugins')
        xdclSettingsFile '''
            settings {
                include ["app"]
            }
        '''
        xdclFile 'app/build.gradle.xdcl', 'application {}'
        // a brings the plugin and defaults the name; b defaults a DIFFERENT cell (version). Both are
        // layer-1 init defaults targeting the same scope but different properties, so they merge —
        // the legitimate counterpart to the conflicting-cell case. (b's `for MyComponent` resolves
        // via a's contributed plugin — per-build-merge; see HANDOFF NOTES.)
        xdclInitScript 'a.gradle.xdcl', '''
            initscript {
                settingsPluginManagement { includedBuilds ["org-plugins"] }
                settingsPlugins [ { id "project-templates" } ]
                defaults { application { name "from-a" } }
            }
        '''
        xdclInitScript 'b.gradle.xdcl', '''
            initscript { defaults { for MyComponent { version "from-b" } } }
        '''

        when:
        executer.usingInitScript(file('a.gradle.xdcl'))
        executer.usingInitScript(file('b.gradle.xdcl'))
        succeeds("help")

        then:
        outputContains 'app { name "from-a", version "from-b"}'
    }

    def "conflicting defaults across two init scripts poison the cell"() {
        given:
        projectTemplates('org-plugins')
        xdclSettingsFile '''
            settings {
                include ["app"]
            }
        '''
        xdclFile 'app/build.gradle.xdcl', 'application { name "x" }'
        // NOTE (per-build-merge): only `a` brings the plugin; `b` only adds a default. `b`'s
        // `for MyComponent` resolves NOT because b is self-contained but because a + b merge into the
        // one build registry before defaults resolve (see HANDOFF NOTES). Both are layer 1 at the same
        // specificity for the same cell, so the cell is poisoned (C-DEFAULT-CONFLICT). If per-file
        // standalone validation is ever added, `b` alone would instead fail to resolve `MyComponent`.
        xdclInitScript 'a.gradle.xdcl', '''
            initscript {
                settingsPluginManagement { includedBuilds ["org-plugins"] }
                settingsPlugins [ { id "project-templates" } ]
                defaults { for MyComponent { version "1.0" } }
            }
        '''
        xdclInitScript 'b.gradle.xdcl', '''
            initscript { defaults { for MyComponent { version "2.0" } } }
        '''

        when:
        executer.usingInitScript(file('a.gradle.xdcl'))
        executer.usingInitScript(file('b.gradle.xdcl'))

        then:
        fails("help")
        failure.assertHasErrorOutput("conflicting defaults for property 'version'")
    }

    def "an unknown block in an init script is a located error"() {
        given:
        enableProblemsApiCheck()
        xdclSettingsFile 'settings {}'
        xdclInitScript 'init.gradle.xdcl', '''
            initscript {
                bogus { x "y" }
            }
        '''

        when:
        executer.usingInitScript(file('init.gradle.xdcl'))

        then:
        fails("help")
        failure.assertHasDescription("${file('init.gradle.xdcl')}:3:17")

        and:
        verifyAll(receivedProblem) {
            definition.id.fqid == 'scripts:xdcl:xdcl-evaluation-error'
            contextualLabel.contains("bogus")
        }
    }

    // The reusable `project-templates` plugin-source build (placed at [path]): a settings plugin
    // contributing an xdcl schema (templates MyApplication / MyLibrary over trait MyComponent) plus
    // reactions that print each configured component, so a test can observe defaults through stdout.
    private void projectTemplates(String path) {
        xdclFile "${path}/settings.gradle.xdcl", 'settings {}'
        buildFile "${path}/build.gradle", '''
            plugins {
              id "java-gradle-plugin"
              id "xdcl-gradle-plugin"
            }
            gradlePlugin {
              plugins {
                projectTemplatesPlugin {
                  id = "project-templates"
                  implementationClass = "my.ProjectTemplatesPlugin"
                }
              }
            }
        '''
        xdslFile "${path}/src/main/xdcl/my.xdsl", '''
            package my.dsl

            trait MyComponent {
              name: String?
              version: String?
            }

            template MyApplication with MyComponent {
              application {}
            }

            template MyLibrary with MyComponent {
              library {}
            }
        '''
        javaFile "${path}/src/main/java/my/ProjectTemplatesPlugin.java", """
            package my;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.initialization.Settings;
            import org.gradle.api.xdcl.*;
            import my.dsl.*;

            @BindReaction(ProjectTemplatesPlugin.ApplicationReaction.class)
            @BindReaction(ProjectTemplatesPlugin.LibraryReaction.class)
            public class ProjectTemplatesPlugin implements Plugin<Settings> {

                static class ApplicationReaction implements Reaction<MyApplication, Project> {
                    @Override public void on(MyApplication data, Project context, ReactionScope scope) {
                        System.out.println("app " + dump(data));
                    }
                }

                static class LibraryReaction implements Reaction<MyLibrary, Project> {
                    @Override public void on(MyLibrary data, Project context, ReactionScope scope) {
                        System.out.println("lib " + dump(data));
                    }
                }

                static String dump(MyComponent data) {
                    return "{ name " + quoted(data.name().get()) + ", version " + quoted(data.version().get()) + "}";
                }

                static String quoted(Object value) {
                    return "\\"" + value + "\\"";
                }

                @Override public void apply(Settings target) {}
            }
        """
    }

    TestFile xdclSettingsFile(String script) {
        file('settings.gradle.xdcl') << script
    }

    TestFile xdclFile(String path, String script) {
        file(path) << script
    }

    TestFile xdslFile(String path, String script) {
        file(path) << script
    }

    TestFile xdclInitScript(String path, String script) {
        file(path) << script
    }
}
