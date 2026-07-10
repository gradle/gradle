/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.performance.generator

import groovy.transform.CompileStatic

@CompileStatic
class TestProjectGenerator extends AbstractTestProjectGenerator {

    TestProjectGeneratorConfiguration config
    FileContentGenerator fileContentGenerator
    BazelFileContentGenerator bazelContentGenerator

    TestProjectGenerator(TestProjectGeneratorConfiguration config) {
        this.config = config
        this.fileContentGenerator = FileContentGenerator.forConfig(config)
        this.bazelContentGenerator = new BazelFileContentGenerator(config)
    }

    def generate(File outputBaseDir) {
        def dependencyTree = new DependencyTree()

        populateDependencyTree(dependencyTree)

        generateProjects(outputBaseDir, dependencyTree)
    }

    def populateDependencyTree(DependencyTree dependencyTree) {
        if (config.subProjects == 0) {
            dependencyTree.calculateClassDependencies(0, 0, config.sourceFiles - 1)
        } else {
            for (int subProjectNumber = 0; subProjectNumber < config.subProjects; subProjectNumber++) {
                def sourceFileRangeStart = subProjectNumber * config.sourceFiles
                def sourceFileRangeEnd = sourceFileRangeStart + config.sourceFiles - 1
                dependencyTree.calculateClassDependencies(subProjectNumber, sourceFileRangeStart, sourceFileRangeEnd)
            }
        }
        dependencyTree.calculateProjectDependencies()
    }

    def generateProjects(File outputBaseDir, DependencyTree dependencyTree) {
        def rootProjectDir = new File(outputBaseDir, config.projectName)
        rootProjectDir.mkdirs()
        generateProject(rootProjectDir, dependencyTree, null, 0)
        for (int subProjectNumber = 0; subProjectNumber < config.subProjects; subProjectNumber++) {
            def subProjectDir = new File(rootProjectDir, "project$subProjectNumber")
            generateProject(subProjectDir, dependencyTree, subProjectNumber, config.projectDepth)
        }
    }

    def generateProject(File projectDir, DependencyTree dependencyTree, Integer subProjectNumber, int projectDepth, int currentDepth = 0) {
        def isRoot = subProjectNumber == null

        file projectDir, config.dsl.fileNameFor('build'), fileContentGenerator.generateBuildGradle(config.language, subProjectNumber, dependencyTree)
        file projectDir, config.dsl.fileNameFor('settings'), fileContentGenerator.generateSettingsGradle(isRoot)
        file projectDir, "gradle.properties", fileContentGenerator.generateGradleProperties(isRoot)
        file projectDir, "pom.xml", fileContentGenerator.generatePomXML(subProjectNumber, dependencyTree)
        file projectDir, "BUILD.bazel", bazelContentGenerator.generateBuildFile(subProjectNumber, dependencyTree)
        if (isRoot) {
            file projectDir, "WORKSPACE", bazelContentGenerator.generateWorkspace()
            file projectDir, "junit.bzl", bazelContentGenerator.generateJunitHelper()
        }
        if (isRoot || config.compositeBuild) {
            file projectDir, "gradle/libs.versions.toml", fileContentGenerator.generateVersionCatalog()
        }
        file projectDir, "performance.scenarios", fileContentGenerator.generatePerformanceScenarios(isRoot)

        if (!isRoot || config.subProjects == 0) {
            def sourceFileRangeStart = isRoot ? 0 : subProjectNumber * config.sourceFiles
            def sourceFileRangeEnd = sourceFileRangeStart + config.sourceFiles - 1
            println "Generating Project: $projectDir"
            (sourceFileRangeStart..sourceFileRangeEnd).each {
                def packageName = fileContentGenerator.packageName(it, subProjectNumber, '/')
                file projectDir, "src/main/${config.language.name}/${packageName}/Production${it}.${config.language.name}", fileContentGenerator.generateProductionClassFile(subProjectNumber, it, dependencyTree)
                file projectDir, "src/test/${config.language.name}/${packageName}/Test${it}.${config.language.name}", fileContentGenerator.generateTestClassFile(subProjectNumber, it, dependencyTree)
            }
        }

        if (isRoot && config.buildSrc) {
            addDummyBuildSrcProject(projectDir)
        }

        if (projectDepth > 0) {
            def subProjectDir = new File(projectDir, "sub${currentDepth}project${subProjectNumber ?: 0}")
            subProjectDir.mkdirs()
            generateProject(subProjectDir, dependencyTree, subProjectNumber, projectDepth - 1, currentDepth + 1)
        }
    }

    /**
     * This is just to ensure we test the overhead of having a buildSrc project, e.g. snapshotting the Gradle API.
     */
    private addDummyBuildSrcProject(File projectDir) {
        file projectDir, "buildSrc/src/main/${config.language.name}/Thing.${config.language.name}", "public class Thing {}"
        file projectDir, "buildSrc/build.gradle", "compileJava.options.incremental = true"
        if (config.deprecationsPerProject > 0) {
            file projectDir, "buildSrc/src/main/java/perf/PerfDeprecationsPlugin.java", generateDeprecationsPlugin(config.deprecationsPerProject)
            file projectDir, "buildSrc/src/main/java/org/gradle/perf/internal/DeprecationDepthHelper.java", generateDeprecationDepthHelper()
            file projectDir, "buildSrc/src/main/resources/META-INF/gradle-plugins/perf-deprecations.properties", "implementation-class=perf.PerfDeprecationsPlugin"
        }
    }

    /**
     * A compiled convention plugin, applied via {@code plugins { id 'perf-deprecations' }}, that fires
     * the given number of deprecations. It lives in a non-Gradle package, so it is the first user frame
     * the location analyser sees, making the deprecations plugin-sourced (no script file location),
     * matching the dominant real-world profile. Messages are unique so console deduplication does not
     * collapse them.
     */
    private static String generateDeprecationsPlugin(int count) {
        """
        package perf;

        import org.gradle.api.Plugin;
        import org.gradle.api.Project;
        import org.gradle.perf.internal.DeprecationDepthHelper;

        public class PerfDeprecationsPlugin implements Plugin<Project> {
            @Override
            public void apply(Project project) {
                for (int i = 0; i < ${count}; i++) {
                    int depth = 8 + (i % 4) * 8;
                    DeprecationDepthHelper.fireAtDepth(depth, "Perf deprecation from " + project.getPath() + " #" + i);
                }
            }
        }
        """.stripIndent()
    }

    /**
     * Fires a deprecation after recursing to the given depth. It lives in an {@code org.gradle.*} package
     * so its frames are classified as internal and skipped by location inference; this lets the recursion
     * vary the depth from the capture point to the first user (plugin) frame, mimicking the varied call
     * depths of real deprecations.
     */
    private static String generateDeprecationDepthHelper() {
        """
        package org.gradle.perf.internal;

        import org.gradle.internal.deprecation.DeprecationLogger;

        public final class DeprecationDepthHelper {
            private DeprecationDepthHelper() {
            }

            public static void fireAtDepth(int depth, String message) {
                if (depth > 0) {
                    fireAtDepth(depth - 1, message);
                    return;
                }
                DeprecationLogger.deprecate(message)
                    .willBecomeAnErrorInGradle10()
                    .withUserManual("feature_lifecycle", "sec:deprecated")
                    .nagUser();
            }
        }
        """.stripIndent()
    }

    static void main(String[] args) {
        def projectName = args[0]
        def outputDir = new File(args[1])

        JavaTestProjectGenerator project = JavaTestProjectGenerator.values().find { it.projectName == projectName }
        if (project == null) {
            throw new IllegalArgumentException("Project not defined: $projectName")
        }
        def projectDir = new File(outputDir, projectName)
        new TestProjectGenerator(project.config).generate(outputDir)

        println "Generated: $projectDir"
    }
}
