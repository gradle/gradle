/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.buildcomparison.render.internal.html;

import groovy.lang.Closure;
import groovy.xml.MarkupBuilder;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.Transformer;
import org.gradle.util.ConfigureUtil;

import java.io.File;

public class HtmlRenderContext {

    private final MarkupBuilder markupBuilder;
    private final Transformer<String, File> relativizer;

    public HtmlRenderContext(MarkupBuilder markupBuilder, Transformer<String, File> relativizer) {
        this.relativizer = relativizer;
        this.markupBuilder = markupBuilder;
    }

    public void render(Closure<?> renderAction) {
        ConfigureUtil.configure(renderAction, markupBuilder);
    }

    public String relativePath(File file) {
        return relativizer.transform(file);
    }

    public String getDiffClass() {
        return "diff";
    }

    public String getEqualClass() {
        return "equal";
    }

    public String getComparisonResultMsgClass() {
        return "comparison-result-msg";
    }

    public String diffClass(Object isEqual) {
        return groovyTrue(isEqual) ? "" : getDiffClass();
    }

    public String equalClass(Object isEqual) {
        return groovyTrue(isEqual) ? getEqualClass() : "";
    }

    public String equalOrDiffClass(Object isEqual) {
        return groovyTrue(isEqual) ? getEqualClass() : getDiffClass();
    }

    private boolean groovyTrue(Object equal) {
        return (Boolean) InvokerHelper.invokeMethod(equal, "asBoolean", null);
    }

}
