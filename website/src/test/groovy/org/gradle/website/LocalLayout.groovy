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

class LocalLayout implements Layout {
    private final Set<String> ignoredPaths = [
            'docs/userguide/userguide.html',
            'docs/userguide/userguide_single.html',
            'docs/userguide/userguide.pdf',
            'docs/javadoc',
            'docs/groovydoc'
    ] as Set

    PageInfo homePage() {
        def uri = new URI(System.getProperty('test.base.uri', new File('website/build/website').toURI() as String))
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
        if (path.startsWith('<?php') || path.matches('[a-zA-Z]+:.+')) {
            return new IgnoredPage(layout, null)
        }
        for (suffix in layout.ignoredPaths) {
            if (path.endsWith(suffix)) {
                return new IgnoredPage(layout, null)
            }
        }
        URI resolved = uri.resolve(path)
        resolved = new URI(resolved.scheme, resolved.authority, resolved.path, null, null)
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
}

class IgnoredPage extends PageInfoImpl {
    def IgnoredPage(LocalLayout layout, URI uri) {
        super(layout, uri);
    }

    boolean isLocal() {
        return false
    }
}
