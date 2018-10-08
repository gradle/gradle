/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.build.docs.dsl.source.model;

import org.gradle.api.Transformer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractLanguageElement implements LanguageElement, Serializable {
    private String rawCommentText;
    private final List<String> annotationNames = new ArrayList<String>();

    protected AbstractLanguageElement() {
    }

    protected AbstractLanguageElement(String rawCommentText) {
        this.rawCommentText = rawCommentText;
    }

    public String getRawCommentText() {
        return rawCommentText;
    }

    public void setRawCommentText(String rawCommentText) {
        this.rawCommentText = rawCommentText;
    }

    public List<String> getAnnotationTypeNames() {
        return annotationNames;
    }

    public void addAnnotationTypeName(String annotationType) {
        annotationNames.add(annotationType);
    }

    public boolean isDeprecated() {
        return annotationNames.contains(Deprecated.class.getName());
    }

    public boolean isIncubating() {
        return annotationNames.contains("org.gradle.api.Incubating");
    }

    public void resolveTypes(Transformer<String, String> transformer) {
        for (int i = 0; i < annotationNames.size(); i++) {
            annotationNames.set(i, transformer.transform(annotationNames.get(i)));
        }
    }
}
