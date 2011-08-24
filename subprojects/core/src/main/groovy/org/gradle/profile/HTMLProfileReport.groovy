/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.profile

import java.text.SimpleDateFormat

import groovy.text.SimpleTemplateEngine
import org.gradle.reporting.TextReportRenderer
import org.gradle.reporting.DurationFormatter

class HTMLProfileReport extends TextReportRenderer<BuildProfile> {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd - HH:mm:ss");
    private static final DurationFormatter DURATION_FORMAT = new DurationFormatter()

    protected void writeTo(BuildProfile profile, Writer out) {
        def templateStream = getClass().getResourceAsStream('/org/gradle/profile/ProfileTemplate.html')
        def template
        try {
            SimpleTemplateEngine engine = new SimpleTemplateEngine()
            template = engine.createTemplate(new InputStreamReader(templateStream))
        } finally {
            templateStream.close()
        }

        def binding = ['build': profile, 'time': DURATION_FORMAT, 'date': DATE_FORMAT]
        def result = template.make(binding)
        result.writeTo(out)
    }
}
