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
package org.gradle.build.docs.dsl;

import org.gradle.build.docs.dsl.model.ClassMetaData;
import org.gradle.build.docs.dsl.model.MethodMetaData;
import org.gradle.build.docs.model.Attachable;
import org.gradle.build.docs.model.ClassMetaDataRepository;
import org.gradle.util.GUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassLinkMetaData implements Serializable, Attachable<ClassLinkMetaData> {
    private final String className;
    private final String simpleName;
    private LinkMetaData.Style style;
    private final Map<String, MethodLinkMetaData> methods = new HashMap<String, MethodLinkMetaData>();

    public ClassLinkMetaData(ClassMetaData classMetaData) {
        this.className = classMetaData.getClassName();
        this.simpleName = classMetaData.getSimpleName();
        this.style = classMetaData.isGroovy() ? LinkMetaData.Style.Groovydoc : LinkMetaData.Style.Javadoc;
        for (MethodMetaData method : classMetaData.getDeclaredMethods()) {
            addMethod(method, method.getOverrideSignature(), style);
        }
    }

    public LinkMetaData getClassLink() {
        return new LinkMetaData(style, simpleName, null);
    }

    public LinkMetaData getMethod(String method) {
        MethodLinkMetaData methodMetaData = findMethod(method);
        return new LinkMetaData(methodMetaData.style, String.format("%s.%s()", simpleName, methodMetaData.name), methodMetaData.id);
    }

    private MethodLinkMetaData findMethod(String method) {
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
            throw new RuntimeException(String.format("No method '%s' found for class '%s'.", method, className));
        }
        if (candidates.size() != 1) {
            throw new RuntimeException(String.format("Found multiple methods called '%s' in class '%s'. Candidates: %s",
                    method, className, GUtil.join(candidates, ", ")));
        }
        return candidates.get(0);
    }

    public LinkMetaData.Style getStyle() {
        return style;
    }

    public void setStyle(LinkMetaData.Style style) {
        this.style = style;
    }

    public void addMethod(MethodMetaData method, String id, LinkMetaData.Style style) {
        methods.put(method.getOverrideSignature(), new MethodLinkMetaData(method.getName(), method.getOverrideSignature(), id, style));
    }

    public void attach(ClassMetaDataRepository<ClassLinkMetaData> linkMetaDataClassMetaDataRepository) {
    }

    private static class MethodLinkMetaData implements Serializable {
        private final String name;
        private final String signature;
        private final String id;
        private final LinkMetaData.Style style;

        private MethodLinkMetaData(String name, String signature, String id, LinkMetaData.Style style) {
            this.name = name;
            this.signature = signature;
            this.id = id;
            this.style = style;
        }

        @Override
        public String toString() {
            return signature;
        }
    }
}
