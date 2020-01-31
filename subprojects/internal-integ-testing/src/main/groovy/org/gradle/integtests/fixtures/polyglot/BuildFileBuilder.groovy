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

@CompileStatic
class BuildFileBuilder extends MultiSectionHandler<BuildFileBuilder> implements PolyglotFileGenerator {
    private final String id
    private String group
    private String version

    BuildFileBuilder(String id) {
        this.id = id
    }

    void setGroup(String group) {
        sections << new GenericSection({
            "group = '$group'"
        }, {
            "group = \"$group\""
        })
    }

    void setVersion(String version) {
        sections << new GenericSection({
            "version = '$version'"
        }, {
            "version = \"$version\""
        })
    }

    BuildFileBuilder applyFrom(String buildFile, GradleDsl targetFileDsl = GradleDsl.GROOVY) {
        sections << new GenericSection({ """apply from: '${targetFileDsl.fileNameFor(buildFile)}'""" }, { """apply(from = "${targetFileDsl.fileNameFor(buildFile)}")""" })
        this
    }

    BuildFileBuilder repositories(@DelegatesTo(value = RepositoriesBuilder, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        newSection(new RepositoriesBuilder()) {
            BuilderSupport.applyConfiguration(spec, it)
        }
        this
    }

    BuildFileBuilder configurations(@DelegatesTo(value = ConfigurationsBuilder, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        newSection(new ConfigurationsBuilder()) {
            BuilderSupport.applyConfiguration(spec, it)
        }
        this
    }

    BuildFileBuilder dependencies(@DelegatesTo(value = DependenciesBuilder, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        newSection(new DependenciesBuilder()) {
            BuilderSupport.applyConfiguration(spec, it)
        }
        this
    }

    @Override
    void generate(GradleDsl dsl, TestFile targetDirectory) {
        switch (dsl) {
            case GradleDsl.GROOVY:
                new GroovyWriter().writeTo(targetDirectory.file("${id}.gradle"))
                break
            case GradleDsl.KOTLIN:
                new KotlinWriter().writeTo(targetDirectory.file("${id}.gradle.kts"))
                break
        }
    }

    class GroovyWriter {
        void writeTo(TestFile buildFile) {
            buildFile << BuilderSupport.prettyPrint("""
                plugins {
                }

                ${sections.collect { it.generateSection(GradleDsl.GROOVY) }.join("\n                ")}
            """)
        }
    }

    class KotlinWriter {
        void writeTo(TestFile buildFile) {
            buildFile << BuilderSupport.prettyPrint("""
                plugins {
                }

                ${sections.collect { it.generateSection(GradleDsl.KOTLIN) }.join("\n                ")}
            """)
        }
    }
}
