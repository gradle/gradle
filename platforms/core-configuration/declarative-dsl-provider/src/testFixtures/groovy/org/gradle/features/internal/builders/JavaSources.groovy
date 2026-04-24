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

import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.declarative.dsl.model.annotations.Adding

/**
 * Stateless Java-source formatting helpers used by the test-fixture builders.
 *
 * <p>Owns name-casing, annotation rendering, and the small property/service
 * emission rules that are shared across {@link DefinitionBuilder},
 * {@link SharedTypeBuilder}, {@link SettingsBuilder}, and {@link PluginClassBuilder}.</p>
 */
class JavaSources {
    static String capitalize(String name) {
        return name.length() == 1 ? name.toUpperCase() : name[0].toUpperCase() + name[1..-1]
    }

    static String decapitalize(String name) {
        return name.length() == 1 ? name.toLowerCase() : name[0].toLowerCase() + name[1..-1]
    }

    /**
     * Renders user-supplied annotation source fragments as a prefix to be inserted immediately
     * before a getter. For an empty list, returns the empty string so existing output is
     * byte-identical. For a non-empty list, returns each annotation joined by a newline + the
     * supplied indent, with a trailing newline + indent so the caller's next token (the getter
     * keyword) sits on a fresh, correctly-indented line.
     */
    static String renderAnnotations(List<String> annotations, String indent) {
        if (annotations.isEmpty()) {
            return ""
        }
        return annotations.collect { "${it}\n${indent}" }.join("")
    }

    static String getPropertyReturnType(PropertyDeclaration property) {
        if (property.sharedTypeRef != null) {
            return property.sharedTypeRef.typeName
        }
        if (property.isReadOnly) {
            return property.type.simpleName
        }
        if (property.isJavaBean) {
            return property.type.simpleName
        }
        if (property.isList) {
            return "ListProperty<${property.type.simpleName}>"
        }
        if (property.type == DirectoryProperty) {
            return "DirectoryProperty"
        }
        if (property.type == RegularFileProperty) {
            return "RegularFileProperty"
        }
        return "Property<${property.type.simpleName}>"
    }

    static String propertyFieldInitializer(PropertyDeclaration property) {
        if (property.type == Directory) {
            return "objects.directoryProperty()"
        }
        if (property.type == RegularFile) {
            return "objects.fileProperty()"
        }
        throw new IllegalStateException("Unsupported property field type: ${property.type}")
    }

    /**
     * Returns true if this property requires a backing field initialized via {@code ObjectFactory}
     * in an abstract class constructor. This applies to {@code Property<Directory>} and
     * {@code Property<RegularFile>} — i.e., non-readOnly, non-javaBean, non-list properties
     * where the type is {@code Directory} or {@code RegularFile}.
     */
    static boolean needsBackingProperty(PropertyDeclaration property) {
        return !property.isReadOnly && !property.isJavaBean && !property.isList &&
            (property.type == Directory || property.type == RegularFile)
    }

    static String generateInjectedServiceDeclarations(List<ServiceDeclaration> services, boolean isAbstract) {
        return services.collect { service ->
            if (isAbstract) {
                """@Inject
                protected abstract ${service.type.name} get${capitalize(service.name)}();"""
            } else {
                """@Inject
                ${service.type.name} get${capitalize(service.name)}();"""
            }
        }.join("\n")
    }

    static String generateAddingMethods(List<PropertyDeclaration> props) {
        def lines = []
        props.findAll { it.isList }.each { property ->
            lines << """
                @${Adding.class.simpleName}
                public void addTo${capitalize(property.name)}(${property.type.simpleName} value) {
                    get${capitalize(property.name)}().add(value);
                }
            """
        }
        return lines.join("\n")
    }
}
