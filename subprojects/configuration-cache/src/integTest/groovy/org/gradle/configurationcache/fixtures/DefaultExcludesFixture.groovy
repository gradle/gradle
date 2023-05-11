/*
 * Copyright 2023 the original author or authors.
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

import org.apache.tools.ant.DirectoryScanner
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

class DefaultExcludesFixture {

    static List<Spec> specs() {
        [
            ScriptLanguage.values(),
            [
                [new RootBuildLocation()],
                [new RootBuildLocation(), new BuildSrcLocation()],
                [new RootBuildLocation(), new IncludedBuildLocation()],
                [new RootBuildLocation(), new BuildSrcLocation(), new IncludedBuildLocation()],
            ]
        ].combinations().collect { language, locations ->
            new Spec(locations, language)
        }
    }

    static class Spec {

        final String copyTask = "copyTask"

        private final List<DefaultExcludesLocation> locations

        private final ScriptLanguage scriptLanguage

        private final List<TestFile> excludedFiles = new ArrayList<>()

        private final List<TestFile> excludedFilesCopies = new ArrayList<>()

        private final List<TestFile> includedFiles = new ArrayList<>()

        private final List<TestFile> includedFilesCopies = new ArrayList<>()

        Spec(List<DefaultExcludesLocation> locations, ScriptLanguage scriptLanguage) {
            this.locations = locations
            this.scriptLanguage = scriptLanguage
        }

        void setup(AbstractIntegrationSpec spec) {
            configureCopyTask(spec)

            locations.forEach {
                it.applyDefaultExcludes(spec, scriptLanguage)

                def includedFile = spec.file("input/${it.includedFileName()}")
                includedFile.text = "input"

                includedFiles.add(includedFile)
                includedFilesCopies.add(spec.file("build/output/${it.includedFileName()}"))

                excludedFiles.add(spec.file("input/${it.excludedFileName()}"))
                excludedFilesCopies.add(spec.file("build/output/${it.excludedFileName()}"))
            }
        }

        void mutateExcludedFiles() {
            excludedFiles.forEach {
                it.text = "Changed!"
            }
        }

        List<TestFile> getExcludedFilesCopies() {
            return excludedFilesCopies
        }

        List<TestFile> getIncludedFilesCopies() {
            return includedFilesCopies
        }

        private String configureCopyTask(AbstractIntegrationSpec spec) {
            if (scriptLanguage == ScriptLanguage.KOTLIN) {
                spec.buildKotlinFile << """
                    tasks.register<Copy>("$copyTask") {
                        from("input")
                        into("build/output")
                    }
                """
            } else {
                spec.buildFile << """
                    task $copyTask(type: Copy) {
                        from("input")
                        into("build/output")
                    }
                """
            }
        }

        @Override
        String toString() {
            return "${locations.join(" and ")} settings${scriptLanguage}"
        }
    }

    static enum ScriptLanguage {

        GROOVY(".gradle"),
        KOTLIN(".gradle.kts")

        private final String extension

        ScriptLanguage(String extension) {
            this.extension = extension
        }

        @Override
        String toString() {
            return extension
        }
    }

    static abstract class DefaultExcludesLocation {

        abstract String includedFileName()

        abstract String excludedFileName()

        abstract void applyDefaultExcludes(AbstractIntegrationSpec spec, ScriptLanguage scriptLanguage)

        private static TestFile settingsFile(TestFile dir, ScriptLanguage scriptLanguage) {
            dir.file("settings${scriptLanguage}")
        }

        static String addDefaultExclude(String excludedFileName) {
            """
            ${DirectoryScanner.name}.addDefaultExclude("**/${excludedFileName}")
            """
        }
    }

    static class RootBuildLocation extends DefaultExcludesLocation {

        @Override
        String includedFileName() {
            return "included-by-root-build.txt"
        }

        @Override
        String excludedFileName() {
            return "excluded-by-root-build.txt"
        }

        @Override
        void applyDefaultExcludes(AbstractIntegrationSpec spec, ScriptLanguage scriptLanguage) {
            settingsFile(spec.testDirectory, scriptLanguage) << addDefaultExclude(excludedFileName())
        }

        @Override
        String toString() {
            return "root build"
        }
    }

    static class IncludedBuildLocation extends DefaultExcludesLocation {

        @Override
        String includedFileName() {
            return "included-by-included-build.txt"
        }

        @Override
        String excludedFileName() {
            return "excluded-by-included-build.txt"
        }

        @Override
        void applyDefaultExcludes(AbstractIntegrationSpec spec, ScriptLanguage scriptLanguage) {
            TestFile includedBuildDir = spec.testDirectory.file("build-logic")
            settingsFile(spec.testDirectory, scriptLanguage) << 'includeBuild("build-logic")'
            settingsFile(includedBuildDir, scriptLanguage) << addDefaultExclude(excludedFileName())
        }

        @Override
        String toString() {
            return "included build"
        }
    }

    static class BuildSrcLocation extends DefaultExcludesLocation {

        @Override
        String includedFileName() {
            return "included-by-buildSrc.txt"
        }

        @Override
        String excludedFileName() {
            return "excluded-by-buildSrc.txt"
        }

        @Override
        void applyDefaultExcludes(AbstractIntegrationSpec spec, ScriptLanguage scriptLanguage) {
            TestFile buildSrcDir = spec.testDirectory.file("buildSrc")
            settingsFile(buildSrcDir, scriptLanguage) << addDefaultExclude(excludedFileName())
        }

        @Override
        String toString() {
            return "buildSrc"
        }
    }
}
