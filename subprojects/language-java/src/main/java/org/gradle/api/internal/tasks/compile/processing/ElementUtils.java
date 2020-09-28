/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.processing;

import com.google.common.collect.Sets;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class ElementUtils {

    public static final String PACKAGE_TYPE_NAME = "package-info";

    public static Set<String> getTopLevelTypeNames(Element[] originatingElements) {
        return getTopLevelTypeNames(Arrays.asList(originatingElements));
    }

    public static Set<String> getTopLevelTypeNames(Collection<? extends Element> originatingElements) {
        if (originatingElements == null || originatingElements.size() == 0) {
            return Collections.emptySet();
        }
        if (originatingElements.size() == 1) {
            String topLevelTypeName = getTopLevelTypeName(originatingElements.iterator().next());
            return Collections.singleton(topLevelTypeName);
        }
        Set<String> typeNames = Sets.newLinkedHashSet();
        for (Element element : originatingElements) {
            // TODO: Support for modules
            if (!element.getKind().name().equals("MODULE")) {
                String topLevelTypeName = getTopLevelTypeName(element);
                typeNames.add(topLevelTypeName);
            }
        }
        return typeNames;
    }

    public static String getTopLevelTypeName(Element originatingElement) {
        Element current = originatingElement;
        Element parent = originatingElement;
        while (parent != null && !(parent instanceof PackageElement)) {
            current = parent;
            parent = current.getEnclosingElement();
        }
        String name = getElementName(current);
        if (name != null) {
            return name;
        }
        throw new IllegalArgumentException("Unexpected element " + originatingElement);
    }

    public static String getElementName(Element current) {
        if (current instanceof PackageElement) {
            String packageName = ((PackageElement) current).getQualifiedName().toString();
            if (packageName.isEmpty()) {
                return PACKAGE_TYPE_NAME;
            } else {
                return packageName + "." + PACKAGE_TYPE_NAME;
            }
        }
        if (current instanceof TypeElement) {
            TypeElement typeElement = (TypeElement) current;
            return typeElement.getQualifiedName().toString();
        }
        return null;
    }

    public static Element getTopLevelType(Element originatingElement) {
        Element current = originatingElement;
        Element parent = originatingElement;
        while (parent != null && !(parent instanceof PackageElement)) {
            current = parent;
            parent = current.getEnclosingElement();
        }
        return current;
    }
}
