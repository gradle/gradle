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

package org.gradle.configurationcache.fixtures

import groovy.transform.Canonical
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.util.internal.GFileUtils.relativePathOf

class MissingScriptFixture {

    static List<Spec> specs() {
        multiLanguageSpecs() + singleLanguageSpecs()
    }

    private static List<Spec> multiLanguageSpecs() {
        [
            ScriptLanguage.values(),
            [
                new MissingBuild(),
                new MissingSettings(),
                new MissingBuildInBuildSrc(),
                new MissingSettingsInBuildSrc(),
                new MissingBuildInIncluded(),
                new MissingSettingsInIncluded()
            ]
        ].combinations().collect { ScriptLanguage scriptLanguage, Scenario scenario ->
            new Spec(scriptLanguage, scenario)
        }
    }

    private static List<Spec> singleLanguageSpecs() {
        [
            new Spec(ScriptLanguage.GROOVY, new MissingBuildTakesPrecedence()),
            new Spec(ScriptLanguage.GROOVY, new MissingSettingsTakesPrecedence())
        ]
    }

    @Canonical
    static class Spec {
        ScriptLanguage scriptLanguage;
        Scenario scenario

        @Override
        String toString() {
            "${scenario.getDisplayName(scriptLanguage.extension)}"
        }

        MissingScriptFixture setUpFixtureFor(AbstractIntegrationSpec abstractIntegrationSpec) {
            scenario.setup(abstractIntegrationSpec)
            return new MissingScriptFixture(scriptLanguage, scenario, abstractIntegrationSpec.testDirectory)
        }
    }

    private static String settingsScriptIn(ScriptLanguage scriptLanguage, String... projects) {
        def includedProjects = Arrays.asList(projects).collect { project -> "\"$project\"" }.join(", ")
        switch (scriptLanguage) {
            case ScriptLanguage.GROOVY:
                return "include $includedProjects"
            case ScriptLanguage.KOTLIN:
                return "include($includedProjects)"
        }
    }

    private static String dummyTaskIn(ScriptLanguage scriptLanguage) {
        switch (scriptLanguage) {
            case ScriptLanguage.GROOVY:
                return 'task ok'
            case ScriptLanguage.KOTLIN:
                return 'tasks.register("ok")'
        }
    }

    enum ScriptLanguage {

        GROOVY(".gradle"),
        KOTLIN(".gradle.kts")

        final String extension

        ScriptLanguage(String extension) {
            this.extension = extension
        }
    }

    static interface Scenario {

        void setup(AbstractIntegrationSpec spec)

        void createInitialBuildLayoutIn(TestFile dir, ScriptLanguage scriptLanguage)

        TestFile addMissingScript(TestFile dir, ScriptLanguage scriptLanguage)

        String getDisplayName(String scriptExtension)
    }

    static class MissingBuild implements Scenario {

        @Override
        void setup(AbstractIntegrationSpec spec) {}

        @Override
        void createInitialBuildLayoutIn(TestFile dir, ScriptLanguage scriptLanguage) {
            dir.file("a", "build$scriptLanguage.extension") << dummyTaskIn(scriptLanguage)
            dir.file("settings$scriptLanguage.extension") << settingsScriptIn(scriptLanguage, "a", "b")
        }

        @Override
        TestFile addMissingScript(TestFile dir, ScriptLanguage scriptLanguage) {
            return dir.file("b", "build$scriptLanguage.extension") << dummyTaskIn(scriptLanguage)
        }

        @Override
        String getDisplayName(String scriptExtension) {
            return "build$scriptExtension"
        }
    }

    static class MissingSettings implements Scenario {

        @Override
        void setup(AbstractIntegrationSpec spec) {
            spec.useTestDirectoryThatIsNotEmbeddedInAnotherBuild()
        }

        @Override
        void createInitialBuildLayoutIn(TestFile dir, ScriptLanguage scriptLanguage) {
            dir.file("build$scriptLanguage.extension") << dummyTaskIn(scriptLanguage)
            dir.file("a", "build$scriptLanguage.extension") << dummyTaskIn(scriptLanguage)
        }

        @Override
        TestFile addMissingScript(TestFile dir, ScriptLanguage scriptLanguage) {
            return dir.file("settings$scriptLanguage.extension") << settingsScriptIn(scriptLanguage, "a")
        }

        @Override
        String getDisplayName(String scriptExtension) {
            return "settings$scriptExtension"
        }
    }

    static class MissingBuildTakesPrecedence implements Scenario {

        @Override
        void setup(AbstractIntegrationSpec spec) {}

        @Override
        void createInitialBuildLayoutIn(TestFile dir, ScriptLanguage scriptLanguage) {
            dir.file("a", "build.gradle.kts") << dummyTaskIn(ScriptLanguage.KOTLIN)
            dir.file("settings$scriptLanguage.extension") << settingsScriptIn(scriptLanguage, "a")
        }

        @Override
        TestFile addMissingScript(TestFile dir, ScriptLanguage scriptLanguage) {
            return dir.file("a", "build$scriptLanguage.extension") << dummyTaskIn(scriptLanguage)
        }

        @Override
        String getDisplayName(String scriptExtension) {
            return "build$scriptExtension over existing build.gradle.kts"
        }
    }

    static class MissingSettingsTakesPrecedence implements Scenario {

        @Override
        void setup(AbstractIntegrationSpec spec) {
            spec.useTestDirectoryThatIsNotEmbeddedInAnotherBuild()
        }

        @Override
        void createInitialBuildLayoutIn(TestFile dir, ScriptLanguage scriptLanguage) {
            dir.file("build$scriptLanguage.extension") << dummyTaskIn(scriptLanguage)
            dir.file("settings.gradle.kts") << ''
        }

        @Override
        TestFile addMissingScript(TestFile dir, ScriptLanguage scriptLanguage) {
            return dir.file("settings$scriptLanguage.extension") << ''
        }

        @Override
        String getDisplayName(String scriptExtension) {
            return "settings$scriptExtension over existing settings.gradle.kts"
        }
    }

    static class MissingBuildInBuildSrc implements Scenario {

        @Override
        void setup(AbstractIntegrationSpec spec) {}

        @Override
        void createInitialBuildLayoutIn(TestFile dir, ScriptLanguage scriptLanguage) {
            dir.file("build$scriptLanguage.extension") << dummyTaskIn(scriptLanguage)
            dir.file("buildSrc", "build$scriptLanguage.extension") << dummyTaskIn(scriptLanguage)
            dir.file("buildSrc", "a", "build$scriptLanguage.extension") << dummyTaskIn(scriptLanguage)
            dir.file("buildSrc", "settings$scriptLanguage.extension") << settingsScriptIn(scriptLanguage, "a", "b")
        }

        @Override
        TestFile addMissingScript(TestFile dir, ScriptLanguage scriptLanguage) {
            return dir.file("buildSrc", "b", "build$scriptLanguage.extension") << dummyTaskIn(scriptLanguage)
        }

        @Override
        String getDisplayName(String scriptExtension) {
            return "build$scriptExtension in buildSrc"
        }
    }

    static class MissingSettingsInBuildSrc implements Scenario {

        @Override
        void setup(AbstractIntegrationSpec spec) {}

        @Override
        void createInitialBuildLayoutIn(TestFile dir, ScriptLanguage scriptLanguage) {
            dir.file("build$scriptLanguage.extension") << dummyTaskIn(scriptLanguage)
            dir.file("buildSrc", "build$scriptLanguage.extension") << dummyTaskIn(scriptLanguage)
            dir.file("buildSrc", "a", "build$scriptLanguage.extension") << dummyTaskIn(scriptLanguage)
        }

        @Override
        TestFile addMissingScript(TestFile dir, ScriptLanguage scriptLanguage) {
            return dir.file("buildSrc", "settings$scriptLanguage.extension") << settingsScriptIn(scriptLanguage, "a")
        }

        @Override
        String getDisplayName(String scriptExtension) {
            return "settings$scriptExtension in buildSrc"
        }
    }

    static class MissingBuildInIncluded implements Scenario {

        @Override
        void setup(AbstractIntegrationSpec spec) {}

        @Override
        void createInitialBuildLayoutIn(TestFile dir, ScriptLanguage scriptLanguage) {
            dir.file("build$scriptLanguage.extension") << dummyTaskIn(scriptLanguage)
            dir.file("settings$scriptLanguage.extension") << 'includeBuild("included-build")'

            dir.file("included-build", "build$scriptLanguage.extension") << dummyTaskIn(scriptLanguage)
            dir.file("included-build", "a", "build$scriptLanguage.extension") << dummyTaskIn(scriptLanguage)
            dir.file("included-build", "settings$scriptLanguage.extension") << settingsScriptIn(scriptLanguage, "a", "b")
        }

        @Override
        TestFile addMissingScript(TestFile dir, ScriptLanguage scriptLanguage) {
            return dir.file("included-build", "b", "build$scriptLanguage.extension") << dummyTaskIn(scriptLanguage)
        }

        @Override
        String getDisplayName(String scriptExtension) {
            return "build$scriptExtension in included build"
        }
    }

    static class MissingSettingsInIncluded implements Scenario {

        @Override
        void setup(AbstractIntegrationSpec spec) {}

        @Override
        void createInitialBuildLayoutIn(TestFile dir, ScriptLanguage scriptLanguage) {
            dir.file("build$scriptLanguage.extension") << dummyTaskIn(scriptLanguage)
            dir.file("settings$scriptLanguage.extension") << 'includeBuild("included-build")'

            dir.file("included-build", "build$scriptLanguage.extension") << dummyTaskIn(scriptLanguage)
            dir.file("included-build", "a", "build$scriptLanguage.extension") << dummyTaskIn(scriptLanguage)
        }

        @Override
        TestFile addMissingScript(TestFile dir, ScriptLanguage scriptLanguage) {
            return dir.file("included-build", "settings$scriptLanguage.extension") << settingsScriptIn(scriptLanguage, "a")
        }

        @Override
        String getDisplayName(String scriptExtension) {
            return "settings$scriptExtension in included build"
        }
    }

    private final ScriptLanguage scriptLanguage
    private final Scenario scenario
    private final TestFile dir
    private TestFile missingScript

    MissingScriptFixture(ScriptLanguage scriptLanguage, Scenario scenario, TestFile dir) {
        this.scriptLanguage = scriptLanguage
        this.scenario = scenario
        this.dir = dir
    }

    void createInitialBuildLayout() {
        scenario.createInitialBuildLayoutIn(dir, scriptLanguage)
    }

    void addMissingScript() {
        missingScript = scenario.addMissingScript(dir, scriptLanguage)
    }

    String getExpectedCacheInvalidationMessage() {
        def path = relativePathOf(missingScript, dir)
        return "Calculating task graph as configuration cache cannot be reused because file '$path' has changed."
    }
}
