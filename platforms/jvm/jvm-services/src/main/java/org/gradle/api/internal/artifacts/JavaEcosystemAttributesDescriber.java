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
package org.gradle.api.internal.artifacts;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmEnvironment;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.internal.attributes.AttributeDescriber;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Describes JVM ecosystem related attributes.
 *
 * Methods on this class that accept {@link Attribute}s and attempt to match them against known values,
 * such as the {@link #getDescribableAttributes()} used by this class, <strong>MUST match on
 * attribute name only, NOT type</strong>.  This allows "desugared" attributes to be described
 * in the same manner.
 */
/* package */ final class JavaEcosystemAttributesDescriber implements AttributeDescriber {
    private static final Attribute<String> STATUS_ATTRIBUTE = Attribute.of("org.gradle.status", String.class);

    private final ImmutableSet<Attribute<?>> describableAttributes = ImmutableSet.of(
        Usage.USAGE_ATTRIBUTE,
        Category.CATEGORY_ATTRIBUTE,
        LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
        Bundling.BUNDLING_ATTRIBUTE,
        TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
        TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
        DocsType.DOCS_TYPE_ATTRIBUTE,
        STATUS_ATTRIBUTE
    );

    /**
     * Checks if the given attribute is describable by this describer.
     *
     * @param attribute the attribute to check
     * @return {@code true} if the given attribute is describable by this describer; {@code false} otherwise
     */
    public boolean isDescribable(Attribute<?> attribute) {
        return describableAttributes.stream().anyMatch(describableAttribute -> haveSameName(attribute, describableAttribute));
    }

    @Override
    public ImmutableSet<Attribute<?>> getDescribableAttributes() {
        return describableAttributes;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public String describeAttributeSet(Map<Attribute<?>, ?> attributes) {
        Object category = extractAttributeValue(attributes, Category.CATEGORY_ATTRIBUTE);
        Object usage = extractAttributeValue(attributes, Usage.USAGE_ATTRIBUTE);
        Object le = extractAttributeValue(attributes, LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE);
        Object bundling = extractAttributeValue(attributes, Bundling.BUNDLING_ATTRIBUTE);
        Object targetJvmEnvironment = extractAttributeValue(attributes, TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE);
        Object targetJvm = extractAttributeValue(attributes, TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE);
        Object docsType = extractAttributeValue(attributes, DocsType.DOCS_TYPE_ATTRIBUTE);
        Object status = extractAttributeValue(attributes, STATUS_ATTRIBUTE);

        StringBuilder sb = new StringBuilder();

        if (category != null) {
            if (docsType != null && toName(category).equals(Category.DOCUMENTATION)) {
                describeDocsType(docsType, sb);
            } else {
                describeCategory(category, sb);
            }
        } else {
            if (docsType != null && category == null) {
                describeDocsType(docsType, sb);
            } else {
                sb.append("a component");
            }
        }
        if (usage != null) {
            sb.append(" for use during ");
            describeUsage(usage, sb);
        }
        if (status != null) {
            sb.append(", with a ");
            describeStatus(status, sb);
        }
        if (targetJvm != null) {
            sb.append(", compatible with ");
            describeTargetJvm(targetJvm, sb);
        }
        if (le != null) {
            sb.append(", ");
            describeLibraryElements(le, sb);
        }
        if (targetJvmEnvironment != null) {
            sb.append(", preferably optimized for ");
            describeTargetJvmEnvironment(targetJvmEnvironment, sb);
        }
        if (bundling != null) {
            sb.append(", and ");
            describeBundling(bundling, sb);
        }
        processExtraAttributes(attributes, sb);
        return sb.toString();
    }

    private static void describeStatus(Object status, StringBuilder sb) {
        sb.append(toName(status)).append(" status");
    }

    @Nullable
    private static <T> Object extractAttributeValue(Map<Attribute<?>, ?> attributes, Attribute<T> attribute) {
        return attributes.entrySet().stream()
            .filter(e -> haveSameName(e.getKey(), attribute))
            .findFirst()
            .map(Map.Entry::getValue)
            .orElse(null);
    }

    private void processExtraAttributes(Map<Attribute<?>, ?> attributes, StringBuilder sb) {
        List<Attribute<?>> describableAttributes = attributes.keySet().stream()
            .filter(a -> !isDescribable(a))
            .sorted(Comparator.comparing(Attribute::getName))
            .collect(Collectors.toList());

        if (!describableAttributes.isEmpty()) {
            sb.append(", as well as ");
            boolean comma = false;
            for (Attribute<?> attribute : describableAttributes) {
                if (comma) {
                    sb.append(", ");
                }
                describeGenericAttribute(sb, attribute, extractAttributeValue(attributes, attribute));
                comma = true;
            }
        }
    }

    @Override
    @Nullable
    public String describeMissingAttribute(Attribute<?> attribute, Object consumerValue) {
        StringBuilder sb = new StringBuilder();
        if (haveSameName(Usage.USAGE_ATTRIBUTE, attribute)) {
            sb.append("its usage (required ");
            describeUsage(consumerValue, sb);
            sb.append(")");
        } else if (haveSameName(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, attribute)) {
            sb.append("its target Java environment (preferred optimized for ");
            describeTargetJvmEnvironment(consumerValue, sb);
            sb.append(")");
        } else if (haveSameName(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, attribute)) {
            sb.append("its target Java version (required compatibility with ");
            describeTargetJvm(consumerValue, sb);
            sb.append(")");
        } else if (haveSameName(Category.CATEGORY_ATTRIBUTE, attribute)) {
            sb.append("its component category (required ");
            describeCategory(consumerValue, sb);
            sb.append(")");
        } else if (haveSameName(Bundling.BUNDLING_ATTRIBUTE, attribute)) {
            sb.append("how its dependencies are found (required ");
            describeBundling(consumerValue, sb);
            sb.append(")");
        } else if (haveSameName(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, attribute)) {
            sb.append("its elements (required them ");
            describeLibraryElements(consumerValue, sb);
            sb.append(")");
        } else if (haveSameName(DocsType.DOCS_TYPE_ATTRIBUTE, attribute)) {
            sb.append("the documentation type (required ");
            describeDocsType(consumerValue, sb);
            sb.append(")");
        } else if (haveSameName(STATUS_ATTRIBUTE, attribute)) {
            sb.append("its status (required ");
            describeStatus(consumerValue, sb);
            sb.append(")");
        } else {
            return null;
        }
        return sb.toString();
    }

    private static void describeGenericAttribute(StringBuilder sb, Attribute<?> attribute, @Nullable Object value) {
        sb.append("attribute '").append(attribute.getName()).append("' with value '").append(value).append("'");
    }

    @Override
    public String describeExtraAttribute(Attribute<?> attribute, Object producerValue) {
        StringBuilder sb = new StringBuilder();
        if (haveSameName(Usage.USAGE_ATTRIBUTE, attribute)) {
            describeUsage(producerValue, sb);
        } else if (haveSameName(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, attribute)) {
            sb.append("compatibility with ");
            describeTargetJvm(producerValue, sb);
        } else if (haveSameName(Category.CATEGORY_ATTRIBUTE, attribute)) {
            describeCategory(producerValue, sb);
        } else if (haveSameName(DocsType.DOCS_TYPE_ATTRIBUTE, attribute)) {
            describeDocsType(producerValue, sb);
        } else if (haveSameName(STATUS_ATTRIBUTE, attribute)) {
            describeStatus(producerValue, sb);
        } else if (haveSameName(Bundling.BUNDLING_ATTRIBUTE, attribute)) {
            describeBundling(producerValue, sb);
        } else if (haveSameName(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, attribute)) {
            sb.append("its elements ");
            describeLibraryElements(producerValue, sb);
        } else {
            describeGenericAttribute(sb, attribute, producerValue);
        }
        return sb.toString();
    }

    private static void describeBundling(Object bundling, StringBuilder sb) {
        String name = toName(bundling);
        switch (name) {
            case Bundling.EXTERNAL:
                sb.append("its dependencies declared externally");
                break;
            case Bundling.EMBEDDED:
                sb.append("its dependencies bundled (fat jar)");
                break;
            case Bundling.SHADOWED:
                sb.append("its dependencies repackaged (shadow jar)");
                break;
            default:
                sb.append("its dependencies found as '").append(name).append("'");
        }
    }

    private static void describeLibraryElements(Object le, StringBuilder sb) {
        String name = toName(le);
        switch (name) {
            case LibraryElements.JAR:
                sb.append("packaged as a jar");
                break;
            case LibraryElements.CLASSES:
                sb.append("preferably in the form of class files");
                break;
            case LibraryElements.RESOURCES:
                sb.append("preferably only the resources files");
                break;
            case LibraryElements.CLASSES_AND_RESOURCES:
                sb.append("preferably not packaged as a jar");
                break;
            default:
                sb.append("with the library elements '").append(name).append("'");
        }
    }

    @SuppressWarnings("deprecation")
    private static void describeUsage(Object usage, StringBuilder sb) {
        String str = toName(usage);
        switch (str) {
            case Usage.JAVA_API:
            case JavaEcosystemSupport.DEPRECATED_JAVA_API_JARS:
            case JavaEcosystemSupport.DEPRECATED_JAVA_API_CLASSES:
                sb.append("compile-time");
                break;
            case Usage.JAVA_RUNTIME:
            case JavaEcosystemSupport.DEPRECATED_JAVA_RUNTIME_JARS:
            case JavaEcosystemSupport.DEPRECATED_JAVA_RUNTIME_CLASSES:
                sb.append("runtime");
                break;
            default:
                sb.append("'").append(str).append("'");
        }
    }

    private static void describeTargetJvm(Object targetJvm, StringBuilder sb) {
        if (targetJvm.equals(Integer.MAX_VALUE)) {
            sb.append("any Java version");
        } else {
            sb.append("Java ").append(targetJvm);
        }
    }

    private static void describeTargetJvmEnvironment(Object targetJvmEnvironment, StringBuilder sb) {
        String name = toName(targetJvmEnvironment);
        switch (name) {
            case TargetJvmEnvironment.STANDARD_JVM:
                sb.append("standard JVMs");
                break;
            case TargetJvmEnvironment.ANDROID:
                sb.append("Android");
                break;
            default:
                sb.append(name);
        }
    }

    private static void describeCategory(Object category, StringBuilder sb) {
        String name = toName(category);
        switch (name) {
            case Category.LIBRARY:
                sb.append("a library");
                break;
            case Category.REGULAR_PLATFORM:
                sb.append("a platform");
                break;
            case Category.ENFORCED_PLATFORM:
                sb.append("an enforced platform");
                break;
            case Category.DOCUMENTATION:
                sb.append("documentation");
                break;
            default:
                sb.append("a component of category '").append(name).append("'");
        }
    }

    private static void describeDocsType(Object docsType, StringBuilder sb) {
        String name = toName(docsType);
        switch (name) {
            case DocsType.JAVADOC:
                sb.append("javadocs");
                break;
            case DocsType.SOURCES:
                sb.append("sources");
                break;
            case DocsType.USER_MANUAL:
                sb.append("a user manual");
                break;
            case DocsType.SAMPLES:
                sb.append("samples");
                break;
            case DocsType.DOXYGEN:
                sb.append("doxygen documentation");
                break;
            default:
                sb.append("documentation of type '").append(name).append("'");
        }
    }

    private static String toName(Object attributeValue) {
        return attributeValue instanceof Category ? ((Named) attributeValue).getName() : String.valueOf(attributeValue);
    }

    /**
     * Checks if two attributes have the same name.
     *
     * @param a first attribute to compare
     * @param b second attribute to compare
     * @return {@code true} if the two attributes have the same name; {@code false} otherwise
     */
    private static boolean haveSameName(Attribute<?> a, Attribute<?> b) {
        return Objects.equals(a.getName(), b.getName());
    }
}
