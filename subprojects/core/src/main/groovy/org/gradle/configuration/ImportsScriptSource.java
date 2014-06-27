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
package org.gradle.configuration;

import org.gradle.internal.resource.DelegatingResource;
import org.gradle.internal.resource.Resource;
import org.gradle.groovy.scripts.DelegatingScriptSource;
import org.gradle.groovy.scripts.ScriptSource;

public class ImportsScriptSource extends DelegatingScriptSource {
    private final ImportsReader importsReader;

    public ImportsScriptSource(ScriptSource source, ImportsReader importsReader) {
        super(source);
        this.importsReader = importsReader;
    }

    @Override
    public Resource getResource() {
        return new ImportsResource(super.getResource());
    }

    private class ImportsResource extends DelegatingResource {
        private ImportsResource(Resource resource) {
            super(resource);
        }

        @Override
        public String getText() {
            String text = getResource().getText();
            assert text != null;

            if (text.matches("\\s*")) {
                return text;
            } else {
                return text + '\n' + importsReader.getImports();
            }
        }
    }
}
