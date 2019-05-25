/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.language.fixtures

import groovy.transform.CompileStatic

import javax.tools.StandardLocation

/**
 * An annotation processor that exclusively generates resources.  Like {@link NonIncrementalProcessorFixture}, can be configured to trigger error cases.
 */
@CompileStatic
class ResourceGeneratingProcessorFixture extends AnnotationProcessorFixture {
    private boolean providesNoOriginatingElements
    private List<String> outputLocations = [StandardLocation.SOURCE_OUTPUT.toString()]

    ResourceGeneratingProcessorFixture() {
        super("Thing")
    }

    ResourceGeneratingProcessorFixture providingNoOriginatingElements() {
        providesNoOriginatingElements = true
        this
    }

    ResourceGeneratingProcessorFixture withOutputLocations(String... locations) {
        outputLocations = Arrays.asList(locations)
        this
    }

    ResourceGeneratingProcessorFixture withOutputLocations(List<String> locations) {
        outputLocations = locations
        this
    }

    String getGeneratorCode() {
        def outputs = outputLocations.collect { """
        resourceFile = filer.createResource($it, \"\", resourceName${providesNoOriginatingElements ? '' : ', element'});
        writer = resourceFile.openWriter();
        try {
            writer.write("We did it.");
        } finally {
            writer.close();
        }
""" }.join("\n        ")

        """
for (Element element : elements) {
    TypeElement typeElement = (TypeElement) element;
    String resourceName = typeElement.getSimpleName().toString() + ".txt";
    FileObject resourceFile;
    Writer writer;
    try {
        $outputs
    } catch (Exception e) {
        messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate resource file " + resourceName + ": " + e.getMessage());
    }
}
"""
    }
}
