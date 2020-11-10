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

package org.gradle.api.plugins.catalog.internal

import com.google.common.collect.Interners
import groovy.transform.Canonical
import org.gradle.api.internal.std.DefaultVersionCatalog
import org.gradle.api.internal.std.DefaultVersionCatalogBuilder
import org.gradle.api.internal.std.ImportConfiguration
import org.gradle.api.internal.std.TomlDependenciesFileParser
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec
import org.gradle.util.TestUtil
import org.jetbrains.annotations.Nullable
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.util.function.Supplier

import static org.gradle.api.internal.std.IncludeExcludePredicate.acceptAll

class TomlWriterTest extends Specification {

    private StringWriter output = new StringWriter()
    private Model sourceModel
    private Model outputModel

    @Subject
    private TomlWriter writer = new TomlWriter(new PrintWriter(output))

    @Unroll
    def "generates an equivalent file from an input (#file)"() {
        parse(file)

        when:
        generateFromModel()

        then:
        outputModel == sourceModel

        where:
        file << [
            'dependencies',
            'dependencies-notations',
        ]
    }

    private void generateFromModel() {
        writer.generate(sourceModel.deps, sourceModel.plugins)
        outputModel = parse(new ByteArrayInputStream(output.toString().getBytes("utf-8")))
    }

    private Model parse(String fileName) {
        sourceModel = parse(this.class.getResourceAsStream("${fileName}.toml"))
    }

    private Model parse(InputStream ins) {
        Map<String, String> plugins = [:]
        def pluginsSpec = new PluginDependenciesSpec() {
            @Override
            PluginDependencySpec id(String id) {
                return new PluginDependencySpec() {
                    @Override
                    PluginDependencySpec version(@Nullable String version) {
                        plugins[id] = version
                        this
                    }

                    @Override
                    PluginDependencySpec apply(boolean apply) {
                        this
                    }
                }
            }
        }
        def builder = new DefaultVersionCatalogBuilder("libs",
            Interners.newStrongInterner(),
            Interners.newStrongInterner(),
            TestUtil.objectFactory(),
            TestUtil.providerFactory(),
            pluginsSpec,
            Stub(Supplier))
        ins.withCloseable {
            TomlDependenciesFileParser.parse(it, builder, pluginsSpec, new ImportConfiguration(acceptAll(), acceptAll(), acceptAll(), acceptAll()))
        }
        return new Model(builder.build(), plugins)
    }

    @Canonical
    private static class Model {
        DefaultVersionCatalog deps
        Map<String, String> plugins
    }
}
