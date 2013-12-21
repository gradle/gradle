/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.sonar

import org.sonar.batch.bootstrapper.Bootstrapper
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.classloader.ClasspathUtil

import org.gradle.api.plugins.sonar.model.SonarRootModel
import org.gradle.util.GFileUtils

/**
 * Analyzes a project hierarchy and writes the results to the
 * Sonar database.
 */
class SonarAnalyze extends ConventionTask {
    /**
     * Entry point to Sonar configuration.
     */
    SonarRootModel rootModel

    @TaskAction
    void analyze() {
        GFileUtils.mkdirs(rootModel.bootstrapDir)
        def bootstrapper = new Bootstrapper("Gradle", rootModel.server.url, rootModel.bootstrapDir)

        def classLoader = bootstrapper.createClassLoader(
                [findGradleSonarJar()] as URL[], SonarAnalyze.classLoader,
                        "groovy", "org.codehaus.groovy", "org.slf4j", "org.apache.log4j", "org.apache.commons.logging",
                                "org.gradle.api.plugins.sonar.model", "ch.qos.logback")

        def analyzerClass = classLoader.loadClass("org.gradle.api.plugins.sonar.internal.SonarCodeAnalyzer")
        def analyzer = analyzerClass.newInstance()
        analyzer.rootModel = rootModel
        analyzer.execute()
    }

    protected URL findGradleSonarJar() {
        def url = ClasspathUtil.getClasspath(SonarAnalyze.classLoader).find { it.path.contains("gradle-sonar") }
        assert url != null, "failed to detect file system location of gradle-sonar Jar"
        url
    }
}