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
package org.gradle.plugins.ide.eclipse;

import org.gradle.api.tasks.Internal;
import org.gradle.plugins.ide.api.PropertiesFileContentMerger;
import org.gradle.plugins.ide.api.PropertiesGeneratorTask;
import org.gradle.plugins.ide.eclipse.model.EclipseJdt;
import org.gradle.plugins.ide.eclipse.model.Jdt;

/**
 * Generates the Eclipse JDT configuration file. If you want to fine tune the eclipse configuration
 * <p>
 * At this moment nearly all configuration is done via {@link EclipseJdt}.
 */
public class GenerateEclipseJdt extends PropertiesGeneratorTask<Jdt> {

    private EclipseJdt jdt;

    public GenerateEclipseJdt() {
        jdt = getInstantiator().newInstance(EclipseJdt.class, new PropertiesFileContentMerger(getTransformer()));
    }

    protected Jdt create() {
        return new Jdt(getTransformer());
    }

    @SuppressWarnings("unchecked")
    protected void configure(Jdt jdtContent) {
        EclipseJdt jdtModel = getJdt();
        jdtModel.getFile().getBeforeMerged().execute(jdtContent);
        jdtContent.setSourceCompatibility(jdtModel.getSourceCompatibility());
        jdtContent.setTargetCompatibility(jdtModel.getTargetCompatibility());
        jdtModel.getFile().getWhenMerged().execute(jdtContent);
    }

    /**
     * Eclipse JDT model that contains information needed to generate the JDT file.
     */
    @Internal
    public EclipseJdt getJdt() {
        return jdt;
    }

    public void setJdt(EclipseJdt jdt) {
        this.jdt = jdt;
    }

}
