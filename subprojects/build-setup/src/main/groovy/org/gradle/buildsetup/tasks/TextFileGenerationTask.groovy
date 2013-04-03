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

package org.gradle.buildsetup.tasks

import groovy.text.SimpleTemplateEngine
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GradleVersion

abstract class TextFileGenerationTask extends DefaultTask {

    @TaskAction
    public void generate() {
        def textOutputFile = getOutputFile()
        if (!textOutputFile.exists()) {
            if (getTemplateURL()!=null){
                SimpleTemplateEngine templateEngine = new SimpleTemplateEngine()
                def binding = [genDate: new Date(), genUser: System.getProperty("user.name"), genGradleVersion: GradleVersion.current().toString()]
                textOutputFile.text = templateEngine.createTemplate(getTemplateURL().text).make(binding).toString();
            }
        }
    }

    abstract URL getTemplateURL()

    @OutputFile
    protected abstract File getOutputFile()
}
