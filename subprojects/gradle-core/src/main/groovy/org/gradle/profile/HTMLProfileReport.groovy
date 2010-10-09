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
import org.gradle.api.UncheckedIOException
import groovy.text.SimpleTemplateEngine


class HTMLProfileReport {
    private BuildProfile profile;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd - HH:mm:ss");
    private static final ElapsedTimeFormatter TIME_FORMAT = new ElapsedTimeFormatter();

    public HTMLProfileReport(BuildProfile profile) {
        this.profile = profile
    }

    public void writeTo(PrintWriter out) {
        try {
            InputStream templateStream = getClass().getResourceAsStream('/org/gradle/profile/ProfileTemplate.html')
            SimpleTemplateEngine engine = new SimpleTemplateEngine()
            def binding = ['build': profile, 'time': TIME_FORMAT, 'date':DATE_FORMAT]
            def result = engine.createTemplate(new InputStreamReader(templateStream)).make(binding)
            result.writeTo(out)
        }
        finally {
            out.close();
        }
    }

    public void writeTo(File file) {
        try {
            FileOutputStream out = new FileOutputStream(file);
            writeTo(new PrintWriter(new BufferedOutputStream(out)));
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
    }


}
