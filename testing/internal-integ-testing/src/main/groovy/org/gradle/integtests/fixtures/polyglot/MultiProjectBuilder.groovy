/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.fixtures.polyglot

import groovy.transform.CompileStatic
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.file.TestFile
import static org.gradle.integtests.fixtures.polyglot.BuilderSupport.applyConfiguration

@CompileStatic
class MultiProjectBuilder {
    private final GradleDsl dsl
    private final TestFile rootProjectDirectory
    private final Map<String, PolyglotProjectBuilder> projects = [:]
    private final Map<String, MultiProjectBuilder> includedBuilds = [:]
    private final SettingsBuilder settingsBuilder = new SettingsBuilder()

    MultiProjectBuilder(GradleDsl dsl, TestFile directory) {
        this.dsl = dsl
        this.rootProjectDirectory = directory
    }

    MultiProjectBuilder settings(@DelegatesTo(value = SettingsBuilder, strategy = Closure.DELEGATE_FIRST) Closure<?> configuration) {
        applyConfiguration(configuration, settingsBuilder)
        this
    }

    MultiProjectBuilder rootProject(GradleDsl projectDsl = dsl, @DelegatesTo(value = PolyglotProjectBuilder, strategy = Closure.DELEGATE_FIRST) Closure<?> config) {
        def p = projects['<root>']
        if (p == null) {
            p = new PolyglotProjectBuilder(projectDsl, rootProjectDirectory)
            projects['<root>'] = p
        }
        applyConfiguration(config, p)
        this
    }

    MultiProjectBuilder project(String name, GradleDsl projectDsl = dsl, @DelegatesTo(value = PolyglotProjectBuilder, strategy = Closure.DELEGATE_FIRST) Closure<?> config) {
        def p = projects[name]
        if (p == null) {
            p = new PolyglotProjectBuilder(projectDsl, rootProjectDirectory.createDir(name))
            projects[name] = p
            settingsBuilder.include(name)
        }
        applyConfiguration(config, p)
        this
    }

    MultiProjectBuilder buildSrc(GradleDsl projectDsl = dsl, @DelegatesTo(value = PolyglotProjectBuilder, strategy = Closure.DELEGATE_FIRST) Closure<?> config) {
        def p = projects['buildSrc']
        if (p == null) {
            p = new PolyglotProjectBuilder(projectDsl, rootProjectDirectory.createDir('buildSrc'))
            projects['buildSrc'] = p
        }
        applyConfiguration(config, p)
        this
    }

    MultiProjectBuilder includedBuild(String includedBuildName, @DelegatesTo(value=MultiProjectBuilder, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        def b = includedBuilds[includedBuildName]
        if (b == null) {
            b = new MultiProjectBuilder(dsl, rootProjectDirectory.createDir(includedBuildName))
            settingsBuilder.includeBuild(includedBuildName)
            includedBuilds[includedBuildName] = b
        }
        applyConfiguration(spec, b)
        this
    }

    void generate() {
        settingsBuilder.generate(dsl, rootProjectDirectory)
        projects.each { name, builder ->
            builder.generate()
        }
        includedBuilds.each { name, builder ->
            builder.generate()
        }
    }
}
