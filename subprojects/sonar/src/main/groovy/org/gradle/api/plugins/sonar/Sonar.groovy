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
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.util.ClasspathUtil

class Sonar extends ConventionTask {
    @Input
    String serverUrl

    @Input
    File bootstrapDir = new File(System.getProperty("java.io.tmpdir"), "sonar-bootstrap")

    @Input
    File projectDir

    @InputDirectory
    Set projectSourceDirs = []

    @InputDirectory
    File projectClassesDir

    @Input
    @Optional
    String projectKey

    @Input
    @Optional
    String projectName

    @Input
    @Optional
    String projectDescription

    @Input
    @Optional
    String projectVersion

    @Input
    Map globalProperties = [:]

    @Input
    Map projectProperties = [:]

    @TaskAction
    void execute() {
        bootstrapDir.mkdirs()
        def bootstrapper = new Bootstrapper("Gradle", getServerUrl(), getBootstrapDir())

        def classLoader = bootstrapper.createClassLoader([findSonarJar()] as URL[],
                Sonar.classLoader, "groovy", "org.codehaus.groovy")

        def launcherClass = classLoader.loadClass("org.gradle.api.plugins.sonar.internal.SonarLauncher")
        def launcher = launcherClass.newInstance()
        launcher.sonarTask = this
        launcher.execute()
    }

    private URL findSonarJar() {
        def url = ClasspathUtil.getClasspath(Sonar.classLoader).find { it.path.contains("gradle-sonar") }
        assert url != null, "failed to detect gradle-sonar Jar"
        url
    }
}