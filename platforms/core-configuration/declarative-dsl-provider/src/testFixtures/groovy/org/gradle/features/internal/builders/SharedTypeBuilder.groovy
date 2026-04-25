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

package org.gradle.features.internal.builders

import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.Definition
import org.gradle.test.fixtures.plugin.PluginBuilder

/**
 * Generates a top-level Java source file for a shared type declared via
 * {@code TestScenarioBuilder.sharedType(...)}.
 *
 * <p>The generated file is either a public interface or a public abstract class,
 * determined by {@link SharedRefKindData#sharedShape} on the declaration. It may declare properties,
 * sub-nested types (rendered as inner interfaces/classes), injected services, and
 * optionally extend {@code Definition<BuildModel>}.</p>
 *
 * <p>The file is always Java, regardless of the scenario's language setting — Kotlin
 * definitions consume it via normal Java interop.</p>
 */
class SharedTypeBuilder {
    private final PropertyTypeDeclaration declaration
    String packageName = "org.gradle.test"

    SharedTypeBuilder(PropertyTypeDeclaration declaration) {
        this.declaration = declaration
    }

    void build(PluginBuilder pluginBuilder) {
        def path = "src/main/java/${packageName.replace('.', '/')}/${declaration.typeName}.java"
        pluginBuilder.file(path).text = declaration.sharedRefData().sharedShape == TypeShape.ABSTRACT_CLASS
            ? generateAbstractClassContent()
            : generateInterfaceContent()
    }

    private String generateInterfaceContent() {
        def extendsClause = declaration.implementsDefinition && declaration.buildModel
            ? "extends ${Definition.class.simpleName}<${declaration.typeName}.${declaration.buildModel.className}>"
            : ""

        def services = JavaSources.generateInjectedServiceDeclarations(declaration.injectedServices, false)

        def propertyGetters = declaration.properties.collect { property ->
            "${JavaSources.renderAnnotations(property.allAnnotations, '                ')}" +
                "${JavaSources.getPropertyReturnType(property)} get${JavaSources.capitalize(property.name)}();"
        }.join("\n")

        def nestedAccessors = declaration.nestedTypes.collect { subNested ->
            if (subNested.kind == NestedKind.NDOC) {
                "${JavaSources.renderAnnotations(subNested.allAnnotations, '                ')}" +
                    "NamedDomainObjectContainer<${subNested.typeName}> get${JavaSources.capitalize(subNested.name)}();"
            } else {
                """${JavaSources.renderAnnotations(subNested.allAnnotations, '                ')}@Nested
                ${subNested.typeName} get${JavaSources.capitalize(subNested.name)}();"""
            }
        }.join("\n\n")

        def nestedInterfaces = NestedRenderer.renderNestedBodies(declaration.nestedTypes, TypeShape.INTERFACE)

        def buildModelInterface = generateBuildModelInterface()

        return """
            package ${packageName};

            import org.gradle.api.Action;
            import org.gradle.api.Named;
            import org.gradle.api.NamedDomainObjectContainer;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.file.DirectoryProperty;
            import org.gradle.api.file.RegularFileProperty;
            import org.gradle.api.file.Directory;
            import org.gradle.api.file.RegularFile;
            import org.gradle.api.tasks.Nested;
            import ${Definition.class.name};
            import ${BuildModel.class.name};

            import javax.inject.Inject;

            public interface ${declaration.typeName} ${extendsClause} {
                ${services}

                ${propertyGetters}

                ${nestedAccessors}

                ${nestedInterfaces}

                ${buildModelInterface}
            }
        """
    }

    private String generateAbstractClassContent() {
        def implementsClause = declaration.implementsDefinition && declaration.buildModel
            ? "implements ${Definition.class.simpleName}<${declaration.typeName}.${declaration.buildModel.className}>"
            : ""

        def nonNdocSubs = declaration.nestedTypes.findAll { it.kind != NestedKind.NDOC }
        def fields = nonNdocSubs.collect { sub ->
            "private final ${sub.typeName} ${sub.name};"
        }.join("\n")

        def constructor
        if (!nonNdocSubs.isEmpty()) {
            def inits = nonNdocSubs.collect { sub ->
                "this.${sub.name} = objects.newInstance(${sub.typeName}.class);"
            }.join("\n                    ")
            constructor = """
                @Inject
                public ${declaration.typeName}(ObjectFactory objects) {
                    ${inits}
                }
            """
        } else {
            constructor = """
                @Inject
                public ${declaration.typeName}() {}
            """
        }

        def services = JavaSources.generateInjectedServiceDeclarations(declaration.injectedServices, true)

        def propertyGetters = declaration.properties.collect { property ->
            "${JavaSources.renderAnnotations(property.allAnnotations, '                ')}" +
                "public abstract ${JavaSources.getPropertyReturnType(property)} get${JavaSources.capitalize(property.name)}();"
        }.join("\n")

        def nestedGetters = declaration.nestedTypes.collect { sub ->
            if (sub.kind == NestedKind.NDOC) {
                "${JavaSources.renderAnnotations(sub.allAnnotations, '                ')}" +
                    "public abstract NamedDomainObjectContainer<${sub.typeName}> get${JavaSources.capitalize(sub.name)}();"
            } else {
                """${JavaSources.renderAnnotations(sub.allAnnotations, '                ')}public ${sub.typeName} get${JavaSources.capitalize(sub.name)}() {
                    return ${sub.name};
                }"""
            }
        }.join("\n\n")

        def nestedTypeBodies = NestedRenderer.renderNestedBodies(declaration.nestedTypes, TypeShape.INTERFACE)

        def buildModelInterface = generateBuildModelInterface()

        return """
            package ${packageName};

            import org.gradle.api.Action;
            import org.gradle.api.Named;
            import org.gradle.api.NamedDomainObjectContainer;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.file.DirectoryProperty;
            import org.gradle.api.file.RegularFileProperty;
            import org.gradle.api.file.Directory;
            import org.gradle.api.file.RegularFile;
            import org.gradle.api.tasks.Nested;
            import ${Definition.class.name};
            import ${BuildModel.class.name};

            import javax.inject.Inject;

            public abstract class ${declaration.typeName} ${implementsClause} {
                ${fields}

                ${constructor}

                ${services}

                ${propertyGetters}

                ${nestedGetters}

                ${nestedTypeBodies}

                ${buildModelInterface}
            }
        """
    }

    private String generateBuildModelInterface() {
        if (!declaration.buildModel) {
            return ""
        }
        def buildModelPropertyGetters = declaration.buildModel.properties.collect { property ->
            "${JavaSources.renderAnnotations(property.allAnnotations, '                ')}" +
                "${JavaSources.getPropertyReturnType(property)} get${JavaSources.capitalize(property.name)}();"
        }.join("\n")
        return """
            public interface ${declaration.buildModel.className} extends BuildModel {
                ${buildModelPropertyGetters}
            }
        """
    }
}
