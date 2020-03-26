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
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.internal.attributes.ConsumerAttributeDescriber;

import java.util.Set;

class JavaEcosystemAttributesDescriber implements ConsumerAttributeDescriber {
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
    public String describe(AttributeContainer attributes) {
        Category category = attributes.getAttribute(Category.CATEGORY_ATTRIBUTE);
        Usage usage = attributes.getAttribute(Usage.USAGE_ATTRIBUTE);
        LibraryElements le = attributes.getAttribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE);
        Bundling bundling = attributes.getAttribute(Bundling.BUNDLING_ATTRIBUTE);
        Integer targetJvm = attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE);

        if (category != null && usage != null && le != null && bundling != null && targetJvm != null) {
            StringBuilder sb = new StringBuilder();
            describeUsage(usage, sb);
            describeCategory(category, sb);
            describeTargetJvm(targetJvm, sb);
            describeLibraryElements(le, sb);
            sb.append("and ");
            describeBundling(bundling, sb);
            return sb.toString();
        }

        return null;
    }

    public void describeBundling(Bundling bundling, StringBuilder sb) {
        switch (bundling.getName()) {
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
                sb.append("its dependencies found as '").append(bundling.getName()).append("'");
        }
    }

    public void describeLibraryElements(LibraryElements le, StringBuilder sb) {
        switch (le.getName()) {
            case LibraryElements.JAR:
                sb.append("packaged as a jar, ");
                break;
            case LibraryElements.CLASSES:
                sb.append("preferably in the form of class files, ");
                break;
            case LibraryElements.RESOURCES:
                sb.append("preferably only the resources files, ");
                break;
            case LibraryElements.CLASSES_AND_RESOURCES:
                sb.append("preferably not packaged as a jar, ");
                break;
            default:
                sb.append("with the library elements '").append(le.getName()).append("', ");
        }
    }

    @SuppressWarnings("deprecation")
    public void describeUsage(Usage usage, StringBuilder sb) {
        switch (usage.getName()) {
            case Usage.JAVA_API:
            case Usage.JAVA_API_CLASSES:
            case Usage.JAVA_API_JARS:
                sb.append("the API of ");
                break;
            case Usage.JAVA_RUNTIME:
            case Usage.JAVA_RUNTIME_CLASSES:
            case Usage.JAVA_RUNTIME_JARS:
                sb.append("the runtime of ");
                break;
            default:
                sb.append("a usage of '").append(usage.getName()).append("' for ");
        }
    }

    public void describeTargetJvm(Integer targetJvm, StringBuilder sb) {
        sb.append("compatible with Java ").append(targetJvm).append(", ");
    }

    public void describeCategory(Category category, StringBuilder sb) {
        switch (category.getName()) {
            case Category.LIBRARY:
                sb.append("a library ");
                break;
            case Category.REGULAR_PLATFORM:
                sb.append("a platform ");
                break;
            case Category.ENFORCED_PLATFORM:
                sb.append("an enforced platform ");
                break;
            default:
                sb.append("a component of category '").append(category.getName()).append("' ");
        }
    }
}
