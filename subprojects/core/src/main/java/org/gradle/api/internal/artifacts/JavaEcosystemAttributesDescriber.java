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
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

class JavaEcosystemAttributesDescriber implements AttributeDescriber {
    private final static Set<Attribute<?>> ATTRIBUTES = ImmutableSet.of(
        Usage.USAGE_ATTRIBUTE,
        Category.CATEGORY_ATTRIBUTE,
        LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
        Bundling.BUNDLING_ATTRIBUTE,
        TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
        TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
        DocsType.DOCS_TYPE_ATTRIBUTE,
        ProjectInternal.STATUS_ATTRIBUTE
    );

    @Override
    public Set<Attribute<?>> getAttributes() {
        return ATTRIBUTES;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public String describeAttributeSet(Map<Attribute<?>, ?> attributes) {
        Object category = attr(attributes, Category.CATEGORY_ATTRIBUTE);
        Object usage = attr(attributes, Usage.USAGE_ATTRIBUTE);
        Object le = attr(attributes, LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE);
        Object bundling = attr(attributes, Bundling.BUNDLING_ATTRIBUTE);
        Object targetJvmEnvironment = attr(attributes, TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE);
        Object targetJvm = attr(attributes, TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE);
        Object docsType = attr(attributes, DocsType.DOCS_TYPE_ATTRIBUTE);
        Object status = attr(attributes, ProjectInternal.STATUS_ATTRIBUTE);

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
    private static <T> Object attr(Map<Attribute<?>, ?> attributes, Attribute<T> attribute) {
        return Cast.uncheckedCast(attributes.get(attribute));
    }

    private void processExtraAttributes(Map<Attribute<?>, ?> attributes, StringBuilder sb) {
        Set<Attribute<?>> remaining = Sets.newLinkedHashSet(attributes.keySet());
        remaining.removeAll(ATTRIBUTES);
        if (!remaining.isEmpty()) {
            sb.append(", as well as ");
            boolean comma = false;
            for (Attribute<?> attribute : remaining) {
                if (comma) {
                    sb.append(", ");
                }
                describeGenericAttribute(sb, attribute, attr(attributes, attribute));
                comma = true;
            }
        }
    }

    @Override
    public String describeMissingAttribute(Attribute<?> attribute, Object consumerValue) {
        StringBuilder sb = new StringBuilder();
        if (Usage.USAGE_ATTRIBUTE.equals(attribute)) {
            sb.append("its usage (required ");
            describeUsage(consumerValue, sb);
            sb.append(")");
        } else if (TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE.equals(attribute)) {
            sb.append("its target Java environment (preferred optimized for ");
            describeTargetJvmEnvironment(consumerValue, sb);
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
        } else if (DocsType.DOCS_TYPE_ATTRIBUTE.equals(attribute)) {
            sb.append("the documentation type (required ");
            describeDocsType(consumerValue, sb);
            sb.append(")");
        } else if (ProjectInternal.STATUS_ATTRIBUTE.equals(attribute)) {
            sb.append("its status (required ");
            describeStatus(consumerValue, sb);
            sb.append(")");
        } else {
            return null;
        }
        return sb.toString();
    }

    public void describeGenericAttribute(StringBuilder sb, Attribute<?> attribute, Object value) {
        sb.append("attribute '").append(attribute.getName()).append("' with value '").append(value).append("'");
    }

    @Override
    public String describeExtraAttribute(Attribute<?> attribute, Object producerValue) {
        StringBuilder sb = new StringBuilder();
        if (sameAttribute(Usage.USAGE_ATTRIBUTE, attribute)) {
            describeUsage(producerValue, sb);
        } else if (sameAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, attribute)) {
            sb.append("compatibility with ");
            describeTargetJvm(producerValue, sb);
        } else if (sameAttribute(Category.CATEGORY_ATTRIBUTE, attribute)) {
            describeCategory(producerValue, sb);
        } else if (sameAttribute(DocsType.DOCS_TYPE_ATTRIBUTE, attribute)) {
            describeDocsType(producerValue, sb);
        } else if (sameAttribute(ProjectInternal.STATUS_ATTRIBUTE, attribute)) {
            describeStatus(producerValue, sb);
        } else if (sameAttribute(Bundling.BUNDLING_ATTRIBUTE, attribute)) {
            describeBundling(producerValue, sb);
        } else if (sameAttribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, attribute)) {
            sb.append("its elements ");
            describeLibraryElements(producerValue, sb);
        } else {
            describeGenericAttribute(sb, attribute, producerValue);
        }
        return sb.toString();
    }

    // A type independent comparator, because of desugaring
    private static boolean sameAttribute(Attribute<?> first, Attribute<?> second) {
        return first.equals(second) || first.getName().equals(second.getName());
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

    private static String toName(Object category) {
        return category instanceof Category ? ((Named) category).getName() : String.valueOf(category);
    }
}
