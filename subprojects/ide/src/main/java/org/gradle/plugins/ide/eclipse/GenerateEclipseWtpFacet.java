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
package org.gradle.plugins.ide.eclipse;

import org.gradle.api.tasks.Internal;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.api.XmlGeneratorTask;
import org.gradle.plugins.ide.eclipse.model.EclipseWtpFacet;
import org.gradle.plugins.ide.eclipse.model.WtpFacet;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;

/**
 * Generates the org.eclipse.wst.common.project.facet.core settings file for Eclipse WTP.
 * If you want to fine tune the eclipse configuration
 * <p>
 * At this moment nearly all configuration is done via {@link EclipseWtpFacet}.
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
public class GenerateEclipseWtpFacet extends XmlGeneratorTask<WtpFacet> {

    private EclipseWtpFacet facet;

    public GenerateEclipseWtpFacet() {
        getXmlTransformer().setIndentation("\t");
        facet = getInstantiator().newInstance(EclipseWtpFacet.class, new XmlFileContentMerger(getXmlTransformer()));
    }

    @Inject
    public GenerateEclipseWtpFacet(EclipseWtpFacet facet) {
        this.facet = facet;
    }

    @Override
    protected WtpFacet create() {
        return new WtpFacet(getXmlTransformer());
    }

    @Override
    protected void configure(WtpFacet xmlFacet) {
        facet.mergeXmlFacet(xmlFacet);
    }

    @Override
    public XmlTransformer getXmlTransformer() {
        if (facet == null) {
            return super.getXmlTransformer();
        }
        return facet.getFile().getXmlTransformer();
    }

    /**
     * The Eclipse WTP facet model containing the details required to generate the settings file.
     */
    @Internal
    public EclipseWtpFacet getFacet() {
        return facet;
    }

    public void setFacet(EclipseWtpFacet facet) {
        this.facet = facet;
    }

}
