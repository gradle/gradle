/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.reflect.validation

import groovy.transform.CompileStatic
import org.gradle.api.internal.DocumentationRegistry

@CompileStatic
trait ValidationMessageChecker {
    private final DocumentationRegistry documentationRegistry = new DocumentationRegistry()

    String userguideLink(String id, String section) {
        documentationRegistry.getDocumentationFor(id, section)
    }

    String learnAt(String id, String section) {
        "Please refer to ${userguideLink(id, section)} for more details about this problem"
    }

    String missingValueMessage(String property, boolean includePropertyName = true) {
        String intro = includePropertyName ? "property '$property' " : ""
        "${intro}doesn't have a configured value. This property isn't marked as optional and no value has been configured. Possible solutions: Assign a value to '${property}' or mark property '${property}' as optional. ${learnAt('validation_problems', 'value_not_set')}."
    }

    String methodShouldNotBeAnnotatedMessage(String type, String kind, String method, String annotation, boolean includeDocLink = false) {
        String message = "Type '$type': $kind '$method()' should not be annotated with: @$annotation. Method '$method' is annotated with an input/output annotation and there is no corresponding getter. Possible solution: Remove the annotations."
        if (includeDocLink) {
            message = "${message} ${learnAt('validation_problems', 'ignored_annotations_on_method')}."
        }
        message
    }

    String privateGetterAnnotatedMessage(String property, String annotation, boolean includeType = true, boolean includeLink = false) {
        String intro = includeType ? "Type 'MyTask': property" : "Property"
        String outro = includeLink ? " ${learnAt("validation_problems", "private_getter_must_not_be_annotated")}." : ""
        "$intro '${property}' is private and annotated with @${annotation}. Annotations on private getters are ignored. Possible solutions: Make the getter public or annotate the public version of the getter.$outro"
    }

    String ignoredAnnotatedPropertyMessage(String property, String ignoringAnnotation, String alsoAnnotatedWith, boolean includeType = true, boolean includeLink = false) {
        String intro = includeType ? "Type 'MyTask': property" : "Property"
        String outro = includeLink ? " ${learnAt("validation_problems", "ignored_property_must_not_be_annotated")}." : ""
        "$intro '${property}' annotated with @${ignoringAnnotation} should not be also annotated with @${alsoAnnotatedWith}. A property is ignored but contains input annotations. Possible solutions: Remove the input annotations or remove the @${ignoringAnnotation} annotation.$outro"
    }
}
