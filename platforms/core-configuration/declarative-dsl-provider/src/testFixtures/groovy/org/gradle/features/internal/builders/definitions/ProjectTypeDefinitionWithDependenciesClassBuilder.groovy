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

package org.gradle.features.internal.builders.definitions

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition
import org.gradle.test.fixtures.plugin.PluginBuilder

/**
 * A {@link ProjectTypeDefinitionClassBuilder} for creating a project type definition that can declare dependencies.
 */
class ProjectTypeDefinitionWithDependenciesClassBuilder extends ProjectTypeDefinitionClassBuilder {
    private String interfaceName = "TestProjectTypeDefinition"

    ProjectTypeDefinitionWithDependenciesClassBuilder() {
        this.publicTypeClassName = "TestProjectTypeDefinitionWithDependencies"
    }

    ProjectTypeDefinitionWithDependenciesClassBuilder parentClassName(String parentClassName) {
        this.interfaceName = parentClassName
        return this
    }

    @Override
    String getPublicTypeClassContent() {
        return """
            package org.gradle.test;

            import org.gradle.api.Action;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.Property;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.artifacts.dsl.DependencyCollector;
            import ${HiddenInDefinition.class.name};
            import ${Adding.class.name};
            import org.gradle.api.tasks.Nested;

            import java.util.List;
            import javax.inject.Inject;

            public abstract class ${publicTypeClassName} implements ${interfaceName} {
                private final Bar bar;
                private boolean isBarConfigured = false;

                @Inject
                public ${publicTypeClassName}(ObjectFactory objects) {
                    bar = objects.newInstance(Bar.class);
                }

                @Nested
                abstract public LibraryDependencies getDependencies();

                @${HiddenInDefinition.class.simpleName}
                public void dependencies(Action<? super LibraryDependencies> action) {
                    action.execute(getDependencies());
                }

                public abstract ListProperty<String> getList();

                @${Adding.class.simpleName}
                public void addToList(String value) {
                    getList().add(value);
                }

                @Nested
                public Bar getBar() {
                    isBarConfigured = true; // TODO: get rid of the side effect in the getter
                    return bar;
                }

                @${HiddenInDefinition.class.simpleName}
                public void bar(Action<? super Bar> action) {
                    action.execute(getBar());
                }

                public abstract static class Bar {

                    public abstract ListProperty<String> getBaz();

                    @${Adding.class.simpleName}
                    public void addToBaz(String value) {
                        getBaz().add(value);
                    }
                }

                public String printDependencies(DependencyCollector collector) {
                    return collector.getDependencies().get().stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", "));
                }

                public String printList(List<?> list) {
                    return list.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", "));
                }

                public String maybeBarConfigure() {
                    return isBarConfigured ? "(bar is configured)" : "";
                }
            }
        """
    }

    @Override
    String displayDefinitionPropertyValues() {
        return super.displayDefinitionPropertyValues() +"""
            ${displayProperty("definition", "list", "definition.printList(definition.getList().get())")}
            ${displayProperty("definition", "bar.baz", "definition.printList(definition.getBar().getBaz().get())")}
            ${displayProperty("definition", "api", "definition.printDependencies(definition.getDependencies().getApi())")}
            ${displayProperty("definition", "implementation", "definition.printDependencies(definition.getDependencies().getImplementation())")}
            ${displayProperty("definition", "runtimeOnly", "definition.printDependencies(definition.getDependencies().getRuntimeOnly())")}
            ${displayProperty("definition", "compileOnly", "definition.printDependencies(definition.getDependencies().getCompileOnly())")}
            System.out.println("definition " + definition.maybeBarConfigure());
        """
    }

    static String getLibraryDependencies() {
        return """
            package org.gradle.test;

            import org.gradle.api.artifacts.dsl.Dependencies;
            import org.gradle.api.artifacts.dsl.DependencyCollector;

            public interface LibraryDependencies extends Dependencies {

                DependencyCollector getApi();

                DependencyCollector getImplementation();

                DependencyCollector getRuntimeOnly();

                DependencyCollector getCompileOnly();

                // CompileOnlyApi is not included here, since both Android and KMP do not support it.
                // Does that mean we should also reconsider if we should support it? Or, should we
                // talk to Android and KMP about adding support
            }
        """
    }

    @Override
    void build(PluginBuilder pluginBuilder) {
        super.build(pluginBuilder)
        pluginBuilder.file("src/main/java/org/gradle/test/LibraryDependencies.java") << libraryDependencies
        new ProjectTypeDefinitionClassBuilder().build(pluginBuilder)
    }
}
