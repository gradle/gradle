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

package org.gradle.plugins.pegdown

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.pegdown.Extensions
import org.gradle.api.InvalidUserDataException
import org.pegdown.PegDownProcessor

class PegDown extends SourceTask {

    @Input
    @Optional
    List<String> options = []

    @Input
    String inputEncoding

    @Input
    String outputEncoding

    private destination
    
    void setDestination(destination) {
        this.destination = destination
    }

    @OutputFile
    File getDestination() {
        project.file(destination)
    }

    @TaskAction
    void process() {
        int optionsValue = getCalculatedOptions()
        PegDownProcessor processor = new PegDownProcessor(optionsValue)
        String markdown = getSource().singleFile.getText(getInputEncoding())
        String html = processor.markdownToHtml(markdown)
        getDestination().write(html, getOutputEncoding())
    }
    
    int getCalculatedOptions() {
        getOptions().inject(0) { acc, val -> acc | toOptionValue(val) } as int
    }
    
    protected int toOptionValue(String optionName) {
        String upName = val.toUpperCase()
        try {
            Extensions."$upName"
        } catch (MissingPropertyException e) {
            throw new InvalidUserDataException("$optionName is not a valid PegDown extension name")
        }
    }
    
    void options(String... options) {
        this.options.addAll(options)
    }
}
