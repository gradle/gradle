/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildinit.plugins.internal

import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import org.gradle.util.GradleVersion
import java.text.DateFormat

class TemplateBasedFileGenerator {
    def generate(URL templateURL, File targetFile, Map specificBindings) {
        targetFile.parentFile.mkdirs()
        SimpleTemplateEngine templateEngine = new SimpleTemplateEngine()
        String now = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date())
        def bindings = [genDate: now, genUser: System.getProperty("user.name"), genGradleVersion: GradleVersion.current().toString()]
        bindings += specificBindings
        Template template = templateEngine.createTemplate(templateURL.text)
        Map wrappedBindings = bindings.collectEntries { key, value -> [key, new TemplateValue(value.toString())] }
        targetFile.withWriter("utf-8") { writer ->
            template.make(wrappedBindings).writeTo(writer)
        }
    }
}
