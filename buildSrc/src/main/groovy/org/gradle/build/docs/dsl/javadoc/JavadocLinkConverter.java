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
package org.gradle.build.docs.dsl.javadoc;

import org.apache.commons.lang.StringUtils;
import org.gradle.build.docs.dsl.model.ClassMetaData;
import org.gradle.build.docs.model.ClassMetaDataRepository;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;

/**
 * Converts a javadoc link into docbook.
 */
public class JavadocLinkConverter {
    private final Document document;
    private final ClassMetaDataRepository<ClassMetaData> metaDataRepository;

    public JavadocLinkConverter(Document document, ClassMetaDataRepository<ClassMetaData> metaDataRepository) {
        this.document = document;
        this.metaDataRepository = metaDataRepository;
    }

    Iterable<? extends Node> resolve(String link, ClassMetaData classMetaData) {
        String className = doResolve(link, classMetaData);
        if (className != null) {
            Element apilink = document.createElement("apilink");
            apilink.setAttribute("class", className);
            return Arrays.asList(apilink);
        } else {
            return Arrays.asList(document.createTextNode(String.format("!!UNKNOWN LINK %s!!", link)));
        }
    }

    private String doResolve(String link, ClassMetaData classMetaData) {
        if (link.contains(".")) {
            return metaDataRepository.find(link) != null ? link : null;
        }

        for (String importedClass : classMetaData.getImports()) {
            String baseName = StringUtils.substringAfterLast(importedClass, ".");
            if (baseName.equals("*")) {
                String candidateClassName = StringUtils.substringBeforeLast(importedClass, ".") + "." + link;
                if (metaDataRepository.find(candidateClassName) != null) {
                    return candidateClassName;
                }
            } else if (link.equals(baseName)) {
                return importedClass;
            }
        }

        String candidateClassName = StringUtils.substringBeforeLast(classMetaData.getClassName(), ".") + "." + link;
        if (metaDataRepository.find(candidateClassName) != null) {
            return candidateClassName;
        }

        return null;
    }
}
