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

/**
 * Stateless emitters for nested-type bodies. Used by {@link DefinitionBuilder} and
 * {@link SharedTypeBuilder}.
 */
class NestedRenderer {

    /**
     * Resolves the effective body shape for a nested type. An explicit shape on the
     * nested wins; otherwise the nested inherits from the enclosing context (the outer
     * definition's shape for top-level nested, or the parent nested's effective shape
     * for sub-nested).
     */
    static TypeShape effectiveShapeOf(PropertyTypeDeclaration nested, TypeShape enclosingShape) {
        if (nested.kind != NestedKind.PLAIN) {
            return enclosingShape
        }
        return nested.plainData().shape ?: enclosingShape
    }

    /**
     * Renders the bodies of each nested type in {@code items}, joined by blank lines.
     * Each body respects the nested type's effective shape (explicit {@code shape}
     * override, or fallback to {@code enclosingEffective}). NDOC, undiscoverable, and
     * shared-ref use their fixed rendering regardless. Sibling build-model interfaces
     * are appended after each non-NDOC body that has one. Shared-ref entries are
     * skipped; their type body is produced by {@link SharedTypeBuilder}.
     */
    static String renderNestedBodies(List<PropertyTypeDeclaration> items, TypeShape enclosingEffective) {
        items.collect { sub ->
            String body
            switch (sub.kind) {
                case NestedKind.SHARED_REF:
                    return ""
                case NestedKind.NDOC:
                    body = sub.implementsDefinition
                        ? generateNdocDefinitionInterface(sub)
                        : generateNdocElementClass(sub)
                    break
                case NestedKind.UNDISCOVERABLE:
                    body = generateUndiscoverableInterface(sub)
                    break
                case NestedKind.PLAIN:
                    def subEff = effectiveShapeOf(sub, enclosingEffective)
                    body = (subEff == TypeShape.ABSTRACT_CLASS)
                        ? generateNestedAbstractClassBody(sub, subEff)
                        : generateNestedInterfaceBody(sub, subEff)
                    break
                default:
                    throw new IllegalStateException("Unreachable: unknown NestedKind ${sub.kind}")
            }
            if (sub.buildModel && sub.kind != NestedKind.NDOC) {
                body += "\n" + generateNestedBuildModelInterface(sub)
            }
            return body
        }.findAll { it }.join("\n\n")
    }

    /**
     * Renders a regular nested type as a public interface body. Sub-nested bodies are
     * dispatched via {@link #renderNestedBodies} with enclosing effective shape
     * {@link TypeShape#INTERFACE}, so each sub inherits INTERFACE unless it overrides.
     */
    static String generateNestedInterfaceBody(PropertyTypeDeclaration nested, TypeShape enclosingEffective) {
        def extendsClause = ""
        if (nested.implementsDefinition && nested.buildModel) {
            extendsClause = "extends ${Definition.class.simpleName}<${nested.buildModel.className}>"
        }

        def propertyGetters = nested.properties.collect { property ->
            "${JavaSources.renderAnnotations(property.allAnnotations)}public abstract ${JavaSources.getPropertyReturnType(property)} get${JavaSources.capitalize(property.name)}();"
        }.join("\n")

        def services = JavaSources.generateInjectedServiceDeclarations(nested.injectedServices, false)

        def nestedAccessors = nested.nestedTypes.collect { sub ->
            renderSubAccessor(sub, false)
        }.join("\n\n")

        def nestedBodies = renderNestedBodies(nested.nestedTypes, TypeShape.INTERFACE)

        return """
public interface ${nested.typeName} ${extendsClause} {
${services}
${propertyGetters}
${nestedAccessors}
${nestedBodies}
}
"""
    }

    /**
     * Renders a regular nested type as a public abstract static class body. Sub-nested
     * accessors use {@code @Nested public abstract} form. Sub-nested bodies are dispatched
     * via {@link #renderNestedBodies} with enclosing effective shape {@link TypeShape#ABSTRACT_CLASS}.
     */
    static String generateNestedAbstractClassBody(PropertyTypeDeclaration nested, TypeShape enclosingEffective) {
        def implementsClause = nested.implementsDefinition && nested.buildModel
            ? "implements ${Definition.class.simpleName}<${nested.buildModel.className}>"
            : ""

        def services = JavaSources.generateInjectedServiceDeclarations(nested.injectedServices, true)

        def propertyGetters = nested.properties.collect { property ->
            "${JavaSources.renderAnnotations(property.allAnnotations)}public abstract ${JavaSources.getPropertyReturnType(property)} get${JavaSources.capitalize(property.name)}();"
        }.join("\n")

        def addingMethods = JavaSources.generateAddingMethods(nested.properties)

        def nestedAccessors = nested.nestedTypes.collect { sub ->
            renderSubAccessor(sub, true)
        }.join("\n\n")

        def nestedBodies = renderNestedBodies(nested.nestedTypes, TypeShape.ABSTRACT_CLASS)

        return """
public abstract static class ${nested.typeName} ${implementsClause} {
public ${nested.typeName}() { }

${services}

${propertyGetters}

${addingMethods}

${nestedAccessors}

${nestedBodies}
}
"""
    }

    /**
     * Renders an NDOC element that implements {@code Definition<BuildModel>}: an interface
     * body with an inner build-model interface (inner-qualified, unlike regular nested
     * build models which are siblings).
     */
    static String generateNdocDefinitionInterface(PropertyTypeDeclaration nested) {
        def extendsClause = nested.buildModel
            ? "extends ${Definition.class.simpleName}<${nested.typeName}.${nested.buildModel.className}>, Named"
            : ""
        def propertyGetters = nested.properties.collect { property ->
            "${JavaSources.renderAnnotations(property.allAnnotations)}public abstract ${JavaSources.getPropertyReturnType(property)} get${JavaSources.capitalize(property.name)}();"
        }.join("\n")
        def subNestedBodies = renderNestedBodies(nested.nestedTypes, TypeShape.INTERFACE)
        def bmIface = ""
        if (nested.buildModel) {
            def buildModelPropertyGetters = nested.buildModel.properties.collect { property ->
                "${JavaSources.renderAnnotations(property.allAnnotations)}${JavaSources.getPropertyReturnType(property)} get${JavaSources.capitalize(property.name)}();"
            }.join("\n")
            bmIface = """
public interface ${nested.buildModel.className} extends BuildModel {
${buildModelPropertyGetters}
}
"""
        }
        return """
public interface ${nested.typeName} ${extendsClause} {
${propertyGetters}
${subNestedBodies}
${bmIface}
}
"""
    }

    /**
     * Renders a sub-nested accessor inside a parent body. NDOC subs always use
     * {@code public abstract NamedDomainObjectContainer<...>}. Regular subs use
     * {@code @Nested public abstract Sub getSub();} when emitted inside an abstract class
     * body, or the unqualified interface-member form {@code @Nested Sub getSub();} inside
     * an interface body. Shared-ref entries emit no accessor (defensive: only top-level).
     */
    static String renderSubAccessor(PropertyTypeDeclaration sub, boolean inAbstractClass) {
        if (sub.kind == NestedKind.SHARED_REF) {
            return ""
        }
        if (sub.kind == NestedKind.NDOC) {
            return "${JavaSources.renderAnnotations(sub.allAnnotations)}public abstract NamedDomainObjectContainer<${sub.typeName}> get${JavaSources.capitalize(sub.name)}();"
        }
        def prefix = inAbstractClass ? "public abstract " : ""
        return """${JavaSources.renderAnnotations(sub.allAnnotations)}@Nested
${prefix}${sub.typeName} get${JavaSources.capitalize(sub.name)}();"""
    }

    static String generateNestedBuildModelInterface(PropertyTypeDeclaration nestedType) {
        if (!nestedType.buildModel) {
            return ""
        }
        def buildModelPropertyGetters = nestedType.buildModel.properties.collect { property ->
            "${JavaSources.renderAnnotations(property.allAnnotations)}${JavaSources.getPropertyReturnType(property)} get${JavaSources.capitalize(property.name)}();"
        }.join("\n")
        return """
public interface ${nestedType.buildModel.className} extends BuildModel {
${buildModelPropertyGetters}
}
"""
    }

    static String generateNdocElementClass(PropertyTypeDeclaration nestedType) {
        def propertyGetters = nestedType.properties.collect { property ->
            "${JavaSources.renderAnnotations(property.allAnnotations)}public abstract ${JavaSources.getPropertyReturnType(property)} get${JavaSources.capitalize(property.name)}();"
        }.join("\n")

        // NDOC element is itself an abstract static class; sub accessors use the abstract-class form.
        def nestedAccessors = nestedType.nestedTypes.collect { sub ->
            renderSubAccessor(sub, true)
        }.join("\n\n")

        // Sub bodies default to INTERFACE (preserves prior behavior where sub-nested inside
        // an NDOC element always rendered as interfaces); each sub can override via its own shape.
        def nestedInterfaces = renderNestedBodies(nestedType.nestedTypes, TypeShape.INTERFACE)

        return """
public abstract static class ${nestedType.typeName} implements Named {
private String name;

public ${nestedType.typeName}(String name) {
this.name = name;
}

@Override
public String getName() {
return name;
}

${propertyGetters}

${nestedAccessors}

${nestedInterfaces}

@Override
public String toString() {
return "${nestedType.typeName}(name = " + name${nestedType.properties.collect { property -> " + \", ${property.name} = \" + get${JavaSources.capitalize(property.name)}().get()" }.join('')} + ")";
}
}
"""
    }

    static String generateUndiscoverableInterface(PropertyTypeDeclaration nestedType) {
        def extendsClause = nestedType.implementsDefinition && nestedType.buildModel
            ? "extends ${Definition.class.simpleName}<${nestedType.typeName}.${nestedType.buildModel.className}>"
            : ""

        def services = JavaSources.generateInjectedServiceDeclarations(nestedType.injectedServices, false)

        def propertyGetters = nestedType.properties.collect { property ->
            "${JavaSources.renderAnnotations(property.allAnnotations)}public abstract ${JavaSources.getPropertyReturnType(property)} get${JavaSources.capitalize(property.name)}();"
        }.join("\n")

        // Undiscoverable is itself an interface; sub accessors use interface-member form.
        def nestedAccessors = nestedType.nestedTypes.collect { sub ->
            renderSubAccessor(sub, false)
        }.join("\n\n")

        def nestedInterfaces = renderNestedBodies(nestedType.nestedTypes, TypeShape.INTERFACE)

        def bmIface = ""
        if (nestedType.buildModel) {
            def buildModelPropertyGetters = nestedType.buildModel.properties.collect { property ->
                "${JavaSources.renderAnnotations(property.allAnnotations)}${JavaSources.getPropertyReturnType(property)} get${JavaSources.capitalize(property.name)}();"
            }.join("\n")
            bmIface = """
public interface ${nestedType.buildModel.className} extends BuildModel {
${buildModelPropertyGetters}
}
"""
        }

        return """
public interface ${nestedType.typeName} ${extendsClause} {
${services}

${propertyGetters}

${nestedAccessors}

${nestedInterfaces}

${bmIface}
}
"""
    }
}
