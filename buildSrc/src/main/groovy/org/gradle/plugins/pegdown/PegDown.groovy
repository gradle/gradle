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

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.pegdown.PegDownProcessor

@CompileStatic
@CacheableTask
class PegDown extends DefaultTask {
    @Input
    String inputEncoding

    @Input
    String outputEncoding

    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    File markdownFile

    @OutputFile
    File destination

    @TaskAction
    void process() {
        PegDownProcessor processor = new PegDownProcessor(0)
        String markdown = markdownFile.getText(getInputEncoding())
        String html = processor.markdownToHtml(markdown)
        getDestination().write(html, getOutputEncoding())
    }
}
