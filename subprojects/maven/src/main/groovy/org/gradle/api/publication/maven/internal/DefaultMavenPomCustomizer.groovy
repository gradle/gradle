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
package org.gradle.api.publication.maven.internal

import org.gradle.api.publication.maven.MavenPomCustomizer
import org.gradle.api.publication.maven.internal.pombuilder.CustomModelBuilder
import org.apache.maven.model.Model

class DefaultMavenPomCustomizer implements MavenPomCustomizer {
    private Closure pomBuilder
    private Closure pomTransformer
    private Closure xmlTransformer

    void apply(Closure pomBuilder) {
        this.pomBuilder = pomBuilder
    }

    void whenConfigured(Closure pomTransformer) {
        this.pomTransformer = pomTransformer
    }

    void withXml(Closure xmlTransformer) {
        this.xmlTransformer = xmlTransformer
    }

    void execute(Model model) {
        executePomBuilder(model)
        executePomTransformer(model)
        executeXmlTransformer(model)
    }

    private void executePomBuilder(Model model) {
        CustomModelBuilder modelBuilder = new CustomModelBuilder(model);
        modelBuilder.project(pomBuilder)
    }

    private void executePomTransformer(Model model) {
        pomTransformer(model)
    }

    private void executeXmlTransformer(Model model) {
        // TODO
    }
}
