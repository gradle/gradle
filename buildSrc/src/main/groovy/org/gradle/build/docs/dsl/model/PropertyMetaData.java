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
package org.gradle.build.docs.dsl.model;

import java.io.Serializable;
import java.util.LinkedList;

public class PropertyMetaData implements Serializable, LanguageElement {
    private String type;
    private boolean writeable;
    private String rawCommentText;
    private final String name;
    private final ClassMetaData ownerClass;

    public PropertyMetaData(String name, ClassMetaData ownerClass) {
        this.name = name;
        this.ownerClass = ownerClass;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return String.format("%s.%s", ownerClass, name);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isWriteable() {
        return writeable;
    }

    public void setWriteable(boolean writeable) {
        this.writeable = writeable;
    }

    public ClassMetaData getOwnerClass() {
        return ownerClass;
    }

    public String getRawCommentText() {
        return rawCommentText;
    }

    public void setRawCommentText(String rawCommentText) {
        this.rawCommentText = rawCommentText;
    }

    public PropertyMetaData getOverriddenProperty() {
        LinkedList<ClassMetaData> queue = new LinkedList<ClassMetaData>();
        queue.add(ownerClass.getSuperClass());
        queue.addAll(ownerClass.getInterfaces());

        while (!queue.isEmpty()) {
            ClassMetaData cl = queue.removeFirst();
            if (cl == null) {
                continue;
            }
            PropertyMetaData overriddenProperty = cl.findDeclaredProperty(name);
            if (overriddenProperty != null) {
                return overriddenProperty;
            }
            queue.add(cl.getSuperClass());
            queue.addAll(cl.getInterfaces());
        }

        return null;
    }
}
