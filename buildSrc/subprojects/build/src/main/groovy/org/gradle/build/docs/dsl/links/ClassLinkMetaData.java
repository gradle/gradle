/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.build.docs.dsl.links;

import org.gradle.build.docs.dsl.source.model.ClassMetaData;
import org.gradle.build.docs.dsl.source.model.EnumConstantMetaData;
import org.gradle.build.docs.dsl.source.model.MethodMetaData;
import org.gradle.build.docs.model.Attachable;
import org.gradle.build.docs.model.ClassMetaDataRepository;
import org.gradle.util.CollectionUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassLinkMetaData implements Serializable, Attachable<ClassLinkMetaData> {
    private final String className;
    private final String simpleName;
    private final String packageName;
    private LinkMetaData.Style style;
    private final Map<String, MethodLinkMetaData> methods = new HashMap<String, MethodLinkMetaData>();

    public ClassLinkMetaData(ClassMetaData classMetaData) {
        this.className = classMetaData.getClassName();
        this.simpleName = classMetaData.getSimpleName();
        this.packageName = classMetaData.getPackageName();
        this.style = LinkMetaData.Style.Javadoc;
        for (MethodMetaData method : classMetaData.getDeclaredMethods()) {
            addMethod(method, style);
        }
        for (EnumConstantMetaData enumConstant : classMetaData.getEnumConstants()) {
            addEnumConstant(enumConstant, style);
        }
    }

    public LinkMetaData getClassLink() {
        return new LinkMetaData(style, simpleName, null);
    }

    public String getPackageName() {
        return packageName;
    }

    public LinkMetaData getMethod(String method) {
        MethodLinkMetaData methodMetaData = findMethod(method);
        String urlFragment = methodMetaData.getUrlFragment(className);
        String displayName = String.format("%s.%s", simpleName, methodMetaData.getDisplayName());
        return new LinkMetaData(methodMetaData.style, displayName, urlFragment);
    }

    private MethodLinkMetaData findMethod(String method) {
        if (method.endsWith("...)")) {
            // Should reuse the link parsing stuff from JavadocLinkConverter instead
            method = method.substring(0, method.length() - 4) + "[])";
        }

        MethodLinkMetaData metaData = methods.get(method);
        if (metaData != null) {
            return metaData;
        }

        List<MethodLinkMetaData> candidates = new ArrayList<MethodLinkMetaData>();
        for (MethodLinkMetaData methodLinkMetaData : methods.values()) {
            if (methodLinkMetaData.name.equals(method)) {
                candidates.add(methodLinkMetaData);
            }
        }
        if (candidates.isEmpty()) {
            String message = String.format("No method '%s' found for class '%s'.\nFound the following methods:", method, className);
            for (MethodLinkMetaData methodLinkMetaData : methods.values()) {
                message += "\n  " + methodLinkMetaData;
            }
            message += "\nThis problem may happen when some apilink from docbook template xmls refers to unknown method."
                    + "\nExample: <apilink class=\"org.gradle.api.Project\" method=\"someMethodThatDoesNotExist\"/>";
            throw new RuntimeException(message);
        }
        if (candidates.size() != 1) {
            String message = String.format("Found multiple methods called '%s' in class '%s'. Candidates: %s",
                    method, className, CollectionUtils.join(", ", candidates));
            message += "\nThis problem may happen when some apilink from docbook template xmls is incorrect. Example:"
                    + "\nIncorrect: <apilink class=\"org.gradle.api.Project\" method=\"tarTree\"/>"
                    + "\nCorrect:   <apilink class=\"org.gradle.api.Project\" method=\"tarTree(Object)\"/>";
            throw new RuntimeException(message);
        }
        return candidates.get(0);
    }

    public LinkMetaData.Style getStyle() {
        return style;
    }

    public void setStyle(LinkMetaData.Style style) {
        this.style = style;
    }

    public void addMethod(MethodMetaData method, LinkMetaData.Style style) {
        methods.put(method.getOverrideSignature(), new MethodLinkMetaData(method.getName(), method.getOverrideSignature(), style));
    }

    public void addEnumConstant(EnumConstantMetaData enumConstant, LinkMetaData.Style style) {
        String name = enumConstant.getName();
        methods.put(name, new EnumConstantLinkMetaData(name, style));
    }

    public void addBlockMethod(MethodMetaData method) {
        methods.put(method.getOverrideSignature(), new BlockLinkMetaData(method.getName(), method.getOverrideSignature()));
    }

    public void addPropertyAccessorMethod(String propertyName, MethodMetaData getterOrSetter) {
        methods.put(getterOrSetter.getOverrideSignature(), new PropertyLinkMetaData(propertyName, getterOrSetter.getName(), getterOrSetter.getOverrideSignature()));
    }

    @Override
    public void attach(ClassMetaDataRepository<ClassLinkMetaData> linkMetaDataClassMetaDataRepository) {
    }

    private static class MethodLinkMetaData implements Serializable {
        final String name;
        final String signature;
        final LinkMetaData.Style style;

        private MethodLinkMetaData(String name, String signature, LinkMetaData.Style style) {
            this.name = name;
            this.signature = signature;
            this.style = style;
        }

        public String getDisplayName() {
            return signature;
        }

        public String getUrlFragment(String className) {
            return style == LinkMetaData.Style.Dsldoc ? String.format("%s:%s", className, signature) : signature.replace('(', '-').replace(')', '-');
        }

        @Override
        public String toString() {
            return signature;
        }
    }

    private static class BlockLinkMetaData extends MethodLinkMetaData {
        private BlockLinkMetaData(String name, String signature) {
            super(name, signature, LinkMetaData.Style.Dsldoc);
        }

        @Override
        public String getDisplayName() {
            return String.format("%s{}", name);
        }
    }

    private static class PropertyLinkMetaData extends MethodLinkMetaData {
        private final String propertyName;

        private PropertyLinkMetaData(String propertyName, String methodName, String signature) {
            super(methodName, signature, LinkMetaData.Style.Dsldoc);
            this.propertyName = propertyName;
        }

        @Override
        public String getUrlFragment(String className) {
            return String.format("%s:%s", className, propertyName);
        }
    }

    private static class EnumConstantLinkMetaData extends MethodLinkMetaData {
        private EnumConstantLinkMetaData(String name, LinkMetaData.Style style) {
            super(name, name, style);
        }

        @Override
        public String getDisplayName() {
            return name;
        }
    }
}
