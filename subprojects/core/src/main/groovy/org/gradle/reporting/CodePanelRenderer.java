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
package org.gradle.reporting;

import org.w3c.dom.Element;

public class CodePanelRenderer extends DomReportRenderer<String> {
    @Override
    public void render(String text, Element parent) {
        // Wrap in a <span>, to work around CSS problem in IE
        Element span = append(parent, "span");
        span.setAttribute("class", "code");
        appendWithText(span, "pre", text);
    }
}
