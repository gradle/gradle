/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.twirl.internal;

import com.google.common.collect.Lists;
import org.gradle.language.base.sources.BaseLanguageSourceSet;
import org.gradle.language.twirl.TwirlImports;
import org.gradle.language.twirl.TwirlSourceSet;
import org.gradle.language.twirl.TwirlTemplateFormat;

import java.util.Arrays;
import java.util.List;

/**
 * Default implementation of a TwirlSourceSet
 */
public class DefaultTwirlSourceSet extends BaseLanguageSourceSet implements TwirlSourceSet {
    private TwirlImports defaultImports = TwirlImports.SCALA;
    private List<TwirlTemplateFormat> userTemplateFormats = Lists.newArrayList();
    private List<String> additionalImports = Lists.newArrayList();

    @Override
    protected String getLanguageName() {
        return "Twirl template";
    }

    @Override
    public TwirlImports getDefaultImports() {
        return defaultImports;
    }

    @Override
    public void setDefaultImports(TwirlImports defaultImports) {
        this.defaultImports = defaultImports;
    }

    @Override
    public List<TwirlTemplateFormat> getUserTemplateFormats() {
        return userTemplateFormats;
    }

    @Override
    public void setUserTemplateFormats(List<TwirlTemplateFormat> userTemplateFormats) {
        this.userTemplateFormats = userTemplateFormats;
    }

    @Override
    public void addUserTemplateFormat(final String extension, String templateType, String... imports) {
        userTemplateFormats.add(new DefaultTwirlTemplateFormat(extension, templateType, Arrays.asList(imports)));
    }

    @Override
    public List<String> getAdditionalImports() {
        return additionalImports;
    }

    @Override
    public void setAdditionalImports(List<String> additionalImports) {
        this.additionalImports = additionalImports;
    }
}
