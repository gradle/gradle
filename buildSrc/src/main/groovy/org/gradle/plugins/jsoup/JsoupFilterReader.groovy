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

package org.gradle.plugins.jsoup

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.gradle.api.Action
import org.gradle.api.file.FileCopyDetails

class JsoupFilterReader extends FilterReader {

    FileCopyDetails fileCopyDetails
    Action<JsoupTransformTarget> action

    JsoupFilterReader(Reader reader) {
        super(new DeferringReader(reader));
        this.in.parent = this
    }

    private static class DeferringReader extends Reader {
        private final Reader source
        private Reader delegate
        private JsoupFilterReader parent

        DeferringReader(Reader source) {
            this.source = source
        }

        int read(char[] cbuf, int off, int len) {
            if (delegate == null) {
                Document document = Jsoup.parse(source.text)
                JsoupTransformTarget target = new JsoupTransformTarget(document, parent.fileCopyDetails)
                parent.action.execute(target)
                delegate = new StringReader(document.toString())
            }

            delegate.read(cbuf, off, len)
        }

        void close() {}
    }
}
