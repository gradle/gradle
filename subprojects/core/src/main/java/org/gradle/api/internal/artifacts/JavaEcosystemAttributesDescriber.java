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
import com.google.common.collect.Sets;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.internal.attributes.AbstractConsumerAttributeDescriber;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

class JavaEcosystemAttributesDescriber extends AbstractConsumerAttributeDescriber {
    private final static Set<Attribute<?>> ATTRIBUTES = ImmutableSet.of(
        Usage.USAGE_ATTRIBUTE,
        Category.CATEGORY_ATTRIBUTE,
        LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
        Bundling.BUNDLING_ATTRIBUTE,
        TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE
    );

    @Override
    public Set<Attribute<?>> getAttributes() {
        return ATTRIBUTES;
    }

    @Override
    public String describeConsumerAttributes(AttributeContainer attributes) {
        Category category = attributes.getAttribute(Category.CATEGORY_ATTRIBUTE);
        Usage usage = attributes.getAttribute(Usage.USAGE_ATTRIBUTE);
        LibraryElements le = attributes.getAttribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE);
        Bundling bundling = attributes.getAttribute(Bundling.BUNDLING_ATTRIBUTE);
        Integer targetJvm = attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE);

        StringBuilder sb = new StringBuilder();
        if (usage != null) {
            describeUsage(usage, sb);
            sb.append(" of ");
        }
        if (category != null) {
            describeCategory(category, sb);
        } else {
            sb.append("a component");
        }
        if (targetJvm != null) {
            sb.append(" compatible with ");
            describeTargetJvm(targetJvm, sb);
        }
        if (le != null) {
            sb.append(", ");
            describeLibraryElements(le, sb);
        }
        if (bundling != null) {
            sb.append(", and ");
            describeBundling(bundling, sb);
        }
        processExtraAttributes(attributes, sb);
        return sb.toString();
    }

    private void processExtraAttributes(AttributeContainer attributes, StringBuilder sb) {
        Set<Attribute<?>> remaining = Sets.newHashSet(attributes.keySet());
        remaining.remove(Category.CATEGORY_ATTRIBUTE);
        remaining.remove(Usage.USAGE_ATTRIBUTE);
        remaining.remove(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE);
        remaining.remove(Bundling.BUNDLING_ATTRIBUTE);
        remaining.remove(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE);
        if (!remaining.isEmpty()) {
            sb.append(", as well as ");
            boolean comma = false;
            for (Attribute<?> attribute : remaining) {
                if (comma) {
                    sb.append(", ");
                }
                describeGenericAttribute(sb, attribute, attributes.getAttribute(attribute));
                comma = true;
            }
        }
    }

    @Override
    public String describeCompatibleAttribute(Attribute<?> attribute, Object consumerValue, Object producerValue) {
        return describeCompatibility(attribute, consumerValue, producerValue, true);
    }

    private static String describeCompatibility(Attribute<?> attribute, Object consumerValue, Object producerValue, boolean compatible) {
        if (Usage.USAGE_ATTRIBUTE.equals(attribute)) {
            return describeExpected(consumerValue, producerValue, (o, sb) -> describeUsage(o, sb));
        }
        if (TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.equals(attribute)) {
            return describeCompatibleTargetJvm(consumerValue, producerValue, compatible);
        }
        if (Category.CATEGORY_ATTRIBUTE.equals(attribute)) {
            return describeExpected(consumerValue, producerValue, (o, sb) -> describeCategory(o, sb));
        }
        if (Bundling.BUNDLING_ATTRIBUTE.equals(attribute)) {
            return describeExpected(consumerValue, producerValue, (o, sb) -> describeBundling(o, sb));
        }
        if (LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE.equals(attribute)) {
            AtomicBoolean first = new AtomicBoolean(true);
            return describeExpected(consumerValue, producerValue, (o, sb) -> {
                sb.append(first.getAndSet(false) ? "its elements " : "them ");
                describeLibraryElements(o, sb);
            });
        }
        return null;
    }

    @Override
    public String describeMissingAttribute(Attribute<?> attribute, Object consumerValue) {
        StringBuilder sb = new StringBuilder("Doesn't say anything about ");
        if (Usage.USAGE_ATTRIBUTE.equals(attribute)) {
            sb.append("its usage (required ");
            describeUsage(consumerValue, sb);
            sb.append(")");
        } else if (TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.equals(attribute)) {
            sb.append("its target Java version (required compatibility with ");
            describeTargetJvm(consumerValue, sb);
            sb.append(")");
        } else if (Category.CATEGORY_ATTRIBUTE.equals(attribute)) {
            sb.append("its component category (required ");
            describeCategory(consumerValue, sb);
            sb.append(")");
        } else if (Bundling.BUNDLING_ATTRIBUTE.equals(attribute)) {
            sb.append("how its dependencies are found (required ");
            describeBundling(consumerValue, sb);
            sb.append(")");
        } else if (LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE.equals(attribute)) {
            sb.append("its elements (required them ");
            describeLibraryElements(consumerValue, sb);
            sb.append(")");
        } else {
            describeGenericAttribute(sb, attribute, consumerValue);
        }
        return sb.toString();
    }

    public void describeGenericAttribute(StringBuilder sb, Attribute<?> attribute, Object value) {
        sb.append("attribute '").append(attribute.getName()).append("' with value '").append(value).append("'");
    }

    @Override
    public String describeExtraAttribute(Attribute<?> attribute, Object producerValue) {
        StringBuilder sb = new StringBuilder("Provides ");
        if (sameAttribute(Usage.USAGE_ATTRIBUTE, attribute)) {
            describeUsage(producerValue, sb);
        } else if (sameAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, attribute)) {
            sb.append("compatibility with ");
            describeTargetJvm(producerValue, sb);
        } else if (sameAttribute(Category.CATEGORY_ATTRIBUTE, attribute)) {
            describeCategory(producerValue, sb);
        } else if (sameAttribute(Bundling.BUNDLING_ATTRIBUTE, attribute)) {
            describeBundling(producerValue, sb);
        } else if (sameAttribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, attribute)) {
            sb.append("its elements ");
            describeLibraryElements(producerValue, sb);
        } else {
            describeGenericAttribute(sb, attribute, producerValue);
        }
        sb.append(" but the consumer didn't ask for it");
        return sb.toString();
    }

    // A type independent comparator, because of desugaring
    private static boolean sameAttribute(Attribute<?> first, Attribute<?> second) {
        return first.equals(second) || first.getName().equals(second.getName());
    }

    @Override
    public String describeIncompatibleAttribute(Attribute<?> attribute, Object consumerValue, Object producerValue) {
        return describeCompatibility(attribute, consumerValue, producerValue, false);
    }

    private static <T> String describeExpected(T consumerValue, T producerValue, BiConsumer<T, StringBuilder> describer) {
        StringBuilder sb = new StringBuilder();
        boolean sameValue = isLikelySameValue(consumerValue, producerValue);
        if (!sameValue) {
            sb.append("Required ");
        } else {
            sb.append("Provides ");
        }
        describer.accept(consumerValue, sb);
        if (!sameValue) {
            sb.append(" and found ");
            describer.accept(producerValue, sb);
        }
        return sb.toString();
    }

    private static String describeCompatibleTargetJvm(Object consumerValue, Object producerValue, boolean compatible) {
        StringBuilder sb = new StringBuilder();
        boolean sameValue = isLikelySameValue(consumerValue, producerValue);
        if (!sameValue) {
            sb.append("Required compatibility with ");
        } else {
            sb.append("Is compatible with ");
        }
        describeTargetJvm(consumerValue, sb);
        if (!sameValue) {
            sb.append(" and found ");
            sb.append(compatible ? " compatible " : "incompatible ");
            describeTargetJvm(producerValue, sb);
        }
        return sb.toString();
    }

    private static void describeBundling(Object bundling, StringBuilder sb) {
        String name = bundling instanceof Bundling ? ((Bundling) bundling).getName() : String.valueOf(bundling);
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
        String name = le instanceof LibraryElements ? ((LibraryElements) le).getName() : String.valueOf(le);
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
                sb.append("with the library elements '").append(name);
        }
    }

    @SuppressWarnings("deprecation")
    private static void describeUsage(Object usage, StringBuilder sb) {
        String str = usage instanceof Usage ? ((Usage) usage).getName() : String.valueOf(usage);
        switch (str) {
            case Usage.JAVA_API:
            case Usage.JAVA_API_CLASSES:
            case Usage.JAVA_API_JARS:
                sb.append("an API");
                break;
            case Usage.JAVA_RUNTIME:
            case Usage.JAVA_RUNTIME_CLASSES:
            case Usage.JAVA_RUNTIME_JARS:
                sb.append("a runtime");
                break;
            default:
                sb.append("a usage of '").append(str).append("'");
        }
    }

    private static void describeTargetJvm(Object targetJvm, StringBuilder sb) {
        sb.append("Java ").append(targetJvm);
    }

    private static void describeCategory(Object category, StringBuilder sb) {
        String name = category instanceof Category ? ((Category) category).getName() : String.valueOf(category);
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
            default:
                sb.append("a component of category '").append(name).append("'");
        }
    }
}
