/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.cli;

import java.util.Map;
import java.util.HashMap;

public class SystemPropertiesCommandLineConverter extends AbstractCommandLineConverter<Map<String, String>> {

    private static final String SYSTEM_PROP = "D";

    public void configure(CommandLineParser parser) {
        parser.option(SYSTEM_PROP, "system-prop").hasArguments().hasDescription("Set system property of the JVM (e.g. -Dmyprop=myvalue).");
    }

    protected Map<String, String> newInstance() {
        return new HashMap<String, String>();
    }

    public Map<String, String> convert(ParsedCommandLine options, Map<String, String> properties) throws CommandLineArgumentException {
        for (String keyValueExpression : options.option(SYSTEM_PROP).getValues()) {
            int pos = keyValueExpression.indexOf("=");
            if (pos < 0) {
                properties.put(keyValueExpression, "");
            } else {
                properties.put(keyValueExpression.substring(0, pos), keyValueExpression.substring(pos+1));
            }
        }
        return properties;
    }
}