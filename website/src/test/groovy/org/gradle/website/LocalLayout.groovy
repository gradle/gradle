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
package org.gradle.website

import java.util.regex.Pattern

class LocalLayout implements Layout {
    private final List<String> docPaths = [
            'docs/userguide',
            'docs/javadoc',
            'docs/groovydoc',
            'docs/dsl'
    ]
    private final List<Pattern> docPatterns
    final boolean ignoreMissingDocs = System.getProperty('test.ignore.docs', 'false').equalsIgnoreCase('true')

    LocalLayout() {
        docPatterns = docPaths.collect { String path ->
            Pattern.compile(".*/[^/]+/${path}(/.+)")
        }
    }

    PageInfo homePage() {
        def uri = new URI(System.getProperty('test.base.uri', new File('build/website').toURI() as String))
        return new LocalPage(this, uri.resolve('index.php'));
    }
}

abstract class PageInfoImpl implements PageInfo {
    private final LocalLayout layout
    private final URI uri

    def PageInfoImpl(LocalLayout layout, URI uri) {
        this.layout = layout
        this.uri = uri
    }

    def String toString() {
        uri
    }

    URI getURI() {
        return uri
    }

    PageInfo resolve(String path) {
        if (path.startsWith('<?php') || path.startsWith("mailto:")) {
            return new IgnoredPage(layout, null, false)
        }

        URI resolved = uri.resolve(path)
        resolved = new URI(resolved.scheme, resolved.authority, resolved.path, null, null)

        for (docPattern in layout.docPatterns) {
            if (path.matches(docPattern)) {
                return new IgnoredPage(layout, resolved, !layout.ignoreMissingDocs)
            }
        }
        if (path.matches('[a-zA-Z]+:.+')) {
            return new IgnoredPage(layout, resolved, true)
        }
        return new LocalPage(layout, resolved)
    }
}

class LocalPage extends PageInfoImpl {
    def LocalPage(LocalLayout layout, URI uri) {
        super(layout, uri);
    }

    boolean isLocal() {
        return true
    }

    boolean mustExist() {
        return true
    }
}

class IgnoredPage extends PageInfoImpl {
    final boolean mustExist

    def IgnoredPage(LocalLayout layout, URI uri, boolean mustExist) {
        super(layout, uri);
        this.mustExist = mustExist
    }

    boolean isLocal() {
        return false
    }

    boolean mustExist() {
        return mustExist
    }
}
