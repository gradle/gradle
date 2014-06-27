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
package org.gradle.plugins.ide.eclipse

import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.api.XmlGeneratorTask
import org.gradle.plugins.ide.eclipse.model.EclipseWtpComponent
import org.gradle.plugins.ide.eclipse.model.WtpComponent

/**
 * Generates the org.eclipse.wst.common.component settings file for Eclipse WTP.
 * If you want to fine tune the eclipse configuration
 * <p>
 * At this moment nearly all configuration is done via {@link EclipseWtpComponent}.
 */
class GenerateEclipseWtpComponent extends XmlGeneratorTask<WtpComponent> {
    /**
     * The Eclipse WTP component model that contains details required to generate the settings file.
     */
    EclipseWtpComponent component

    GenerateEclipseWtpComponent() {
        xmlTransformer.indentation = "\t"
        component = instantiator.newInstance(EclipseWtpComponent, project, new XmlFileContentMerger(xmlTransformer))
    }

    @Override protected WtpComponent create() {
        new WtpComponent(xmlTransformer)
    }

    @Override protected void configure(WtpComponent xmlComponent) {
        component.mergeXmlComponent(xmlComponent)
    }
}
