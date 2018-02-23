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
package org.gradle.build.docs

import org.w3c.dom.Element

/**
 * Validates sample element - looks for missing attributes.
 */
public class SampleElementValidator {

    void validate(Element element) {
        String id = element.'@id'
        if (!id) {
            throw new RuntimeException("No id attribute specified for sample.")
        }
        String srcDir = element.'@dir'
        if (!srcDir) {
            throw new RuntimeException("No dir attribute specified for sample '$id'.")
        }
        String title = element.'@title'
        if (!title) {
            throw new RuntimeException("No title attribute specified for sample '$id'.")
        }
        element.children().each {Element child ->
            switch (child.name()) {
                case 'sourcefile':
                    if (!child.'@file') {
                        throw new RuntimeException("No file attribute specified for source file in sample '$id'.")
                    }
                    break
                case 'output':
                case 'test':
                    if (child.'@args' == null) {
                        throw new RuntimeException(" No args attribute specified for output for sample '$id'. ")
                    }
                    break;
                case 'layout':
                    // nothing, makes no sense to do the validation here, cause I'd have to copy all the logic also
                    break;
                default:
                    throw new RuntimeException("Unrecognised sample type ${child.name()} found.")
            }
        }
    }

}
