/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.plugins.sonar.runner

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.jvm.Jvm

/**
 * A plugin for analyzing projects with the
 * <a href="http://docs.codehaus.org/display/SONAR/Analyzing+with+Sonar+Runner">Sonar Runner</a>.
 * When applied to a project, both the project itself and its subprojects
 * will be analyzed (in a single run). Therefore, it's common to apply the
 * plugin only to the root project. To exclude selected subprojects from
 * being analyzed, set {@code sonarRunner.skipProject = true}.
 *
 * <p>The plugin is configured via {@link SonarRunnerExtension}. Here is a
 * small example:
 *
 * <pre autoTested=''>
 * sonarRunner {
 *     skipProject = false // this is the default
 *
 *     sonarProperties {
 *         property "sonar.host.url", "http://my.sonar.server" // adding a single property
 *         properties mapOfProperties // adding multiple properties at once
 *         properties["sonar.sources"] += sourceSets.other.java.srcDirs // manipulating an existing property
 *     }
 * }
 * </pre>
 *
 * The Sonar Runner already comes with defaults for some of the most important
 * Sonar properties (server URL, database settings, etc.). For details see
 * <a href="http://docs.codehaus.org/display/SONAR/Analysis+Parameters">Analysis Parameters</a>
 * in the Sonar documentation. The {@code sonar-runner} plugin provides the following additional
 * defaults:
 *
 * <dl>
 *     <dt>sonar.projectKey
 *     <dd>"$project.group:$project.name"
 *     <dt>sonar.projectName
 *     <dd>project.name
 *     <dt>sonar.projectDescription
 *     <dd>project.description
 *     <dt>sonar.projectVersion
 *     <dd>sonar.version
 *     <dt>sonar.projectBaseDir
 *     <dd>project.projectDir
 *     <dt>sonar.working.directory
 *     <dd>"$project.buildDir/sonar"
 *     <dt>sonar.dynamicAnalysis
 *     <dd>"reuseReports"
 * </dl>
 *
 * For project that have the {@code java-base} plugin applied, additionally the following defaults are provided:
 *
 * <dl>
 *     <dt>sonar.java.source
 *     <dd>project.sourceCompatibility
 *     <dt>sonar.java.target
 *     <dd>project.targetCompatibility
 * </dl>
 *
 * For project that have the {@code java} plugin applied, additionally the following defaults are provided:
 *
 * <dl>
 *     <dt>sonar.sources
 *     <dd>sourceSets.main.allSource.srcDirs (filtered to only include existing directories)
 *     <dt>sonar.tests
 *     <dd>sourceSets.test.allSource.srcDirs (filtered to only include existing directories)
 *     <dt>sonar.binaries
 *     <dd>sourceSets.main.runtimeClasspath (filtered to only include directories)
 *     <dt>sonar.libraries
 *     <dd>sourceSets.main.runtimeClasspath (filtering to only include files; {@code rt.jar} added if necessary)
 *     <dt>sonar.surefire.reportsPath
 *     <dd>test.testResultsDir (if the directory exists)
 * </dl>
 */
@Incubating
class SonarRunnerPlugin implements Plugin<Project> {
    // the project to which the plugin was applied
    Project targetProject

    void apply(Project project) {
        targetProject = project
        def sonarRunnerTask = project.tasks.add("sonarRunner", SonarRunner)
        sonarRunnerTask.conventionMapping.with {
            sonarProperties = {
                def properties = new Properties()
                computeSonarProperties(project, properties)
                properties
            }
        }
        project.allprojects {
            extensions.create("sonarRunner", SonarRunnerExtension)
        }
        sonarRunnerTask.dependsOn {
            project.allprojects.findAll { prj ->
                prj.plugins.hasPlugin(JavaPlugin) && !prj.sonarRunner.skipProject
            }.collect { it.tasks.test }
        }
    }

    void computeSonarProperties(Project project, Properties properties) {
        def extension = project.extensions.getByType(SonarRunnerExtension)
        if (extension.skipProject) { return }

        Map<String, Object> rawProperties = [:]
        addGradleDefaults(project, rawProperties)
        extension.evaluateSonarPropertiesBlocks(rawProperties)
        if (project == targetProject) { addSystemProperties(rawProperties) }

        def projectPrefix = project.path.substring(targetProject.path.size()).replace(":", ".")
        if (projectPrefix.startsWith(".")) {
            projectPrefix = projectPrefix.substring(1)
        }
        convertProperties(rawProperties, projectPrefix, properties)
        
        def enabledChildProjects = project.childProjects.values().findAll { !it.sonarRunner.skipProject }
        if (enabledChildProjects.empty) { return }

        properties[convertKey("sonar.modules", projectPrefix)] = convertValue(enabledChildProjects.name)
        for (childProject in enabledChildProjects) {
            computeSonarProperties(childProject, properties)
        }
    }

    private void addGradleDefaults(Project project, Map<String, Object> properties) {
        // for some reason, sonar.sources must always be set (as of Sonar 3.4)
        properties["sonar.sources"] = ""

        properties["sonar.projectKey"] = getProjectKey(project)
        properties["sonar.projectName"] = project.name
        properties["sonar.projectDescription"] = project.description
        properties["sonar.projectVersion"] = project.version
        properties["sonar.projectBaseDir"] = project.projectDir
        properties["sonar.dynamicAnalysis"] = "reuseReports"

        if (project == targetProject) {
            properties["sonar.environment.information.key"] = "Gradle"
            properties["sonar.environment.information.version"] = project.gradle.gradleVersion
            properties["sonar.working.directory"] = new File(project.buildDir, "sonar")
        }

        project.plugins.withType(JavaBasePlugin) {
            properties["sonar.java.source"] = project.sourceCompatibility
            properties["sonar.java.target"] = project.targetCompatibility
        }

        project.plugins.withType(JavaPlugin) {
            SourceSet main = project.sourceSets.main
            SourceSet test = project.sourceSets.test

            properties["sonar.sources"] = main.allSource.srcDirs.findAll { it.exists() } ?: null
            properties["sonar.tests"] = test.allSource.srcDirs.findAll { it.exists() } ?: null
            properties["sonar.binaries"] = main.runtimeClasspath.findAll { it.directory } ?: null
            properties["sonar.libraries"] = getLibraries(main)
            properties["sonar.surefire.reportsPath"] = project.test.testResultsDir.exists() ? project.test.testResultsDir : null
        }
    }

    private String getProjectKey(Project project) {
        String result
        if (project.parent) {
            result = "$project.group:$project.rootProject.name$project.path"
        } else {
            result = "$project.group:$project.name"
        }

        // project key gets used in URL parameter
        // example: http://localhost:63450/batch_bootstrap/properties?project=org.gradle.test.sonar:Sonar+Test+Build
        URLEncoder.encode(result, "utf-8")
    }

    private void addSystemProperties(Map<String, Object> properties) {
        properties.putAll(System.properties.findAll { key, value -> key.startsWith("sonar.") })
    }

    private Collection<File> getLibraries(SourceSet main) {
        def libraries = main.runtimeClasspath.findAll { it.file }
        def runtimeJar = Jvm.current().runtimeJar
        if (runtimeJar != null) {
            libraries << runtimeJar
        }
        libraries ?: null
    }

    private void convertProperties(Map<String, Object> rawProperties, String projectPrefix, Properties properties) {
        rawProperties.each { key, value ->
            if (value != null) {
                properties[convertKey(key, projectPrefix)] = convertValue(value)
            }
        }
    }
    
    private String convertKey(String key, String projectPrefix) {
        projectPrefix ? "${projectPrefix}.$key" : key
    }
    
    private String convertValue(Object value) {
        value instanceof Iterable ? value.collect { convertValue(it) }.join(",") : value.toString()
    }
}
