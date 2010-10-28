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

import org.junit.rules.MethodRule
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement
import com.gargoylesoftware.htmlunit.*
import com.gargoylesoftware.htmlunit.html.HtmlPage
import com.gargoylesoftware.htmlunit.html.HtmlAnchor

class HtmlUnitBrowser implements MethodRule, Browser {
    private WebClient client
    private Layout layout

    def HtmlUnitBrowser(Layout layout) {
        this.layout = layout;
    }

    Statement apply(Statement base, FrameworkMethod method, Object target) {
        return new Statement() {
            void evaluate() {
                try {
                    base.evaluate()
                } finally {
                    close()
                }
            }
        }
    }

    def close() {
        client?.closeAllWindows()
    }

    WebsitePage open(PageInfo pageInfo) {
        if (client == null) {
            client = new WebClient()
            client.javaScriptEnabled = false
            client.pageCreator = new DefaultPageCreator() {
                def Page createPage(WebResponse webResponse, WebWindow webWindow) {
                    if (webResponse.requestUrl.path.endsWith('.php')) {
                        return createHtmlPage(webResponse, webWindow)
                    }
                    return super.createPage(webResponse, webWindow);
                }
            }
        }

        URI uri = pageInfo.URI
        HtmlPage page
        try {
            page = client.getPage(uri.toURL())
        } catch (FileNotFoundException e) {
            throw new PageNotFoundException("Page $pageInfo not found.", e)
        }
        return new WebsitePageImpl(this, page, pageInfo)
    }
}

class WebsitePageImpl implements WebsitePage {
    private final HtmlPage page
    private final PageInfo info
    private final HtmlUnitBrowser browser

    def WebsitePageImpl(HtmlUnitBrowser browser, HtmlPage page, PageInfo info) {
        this.browser = browser
        this.page = page
        this.info = info
    }

    def String toString() {
        return page as String
    }

    boolean isLocal() {
        return info.isLocal()
    }

    URI getURI() {
        return info.URI
    }

    PageInfo resolve(String path) {
        return info.resolve(path)
    }

    Collection<Link> getLocalLinks() {
        return page.anchors.inject([]) { List<Link> list, HtmlAnchor anchor ->
            if (!anchor.hrefAttribute) {
                return list
            }
            PageInfo info = info.resolve(anchor.hrefAttribute)
            if (!info.isLocal()) {
                return list
            }
            list << new LinkImpl(browser, anchor, this.info, info)
        }
    }
}

class BinaryWebsitePage implements WebsitePage {
    private final PageInfo page

    BinaryWebsitePage(PageInfo page) {
        this.page = page;
    }

    @Override
    String toString() {
        return page.toString()
    }

    boolean isLocal() {
        return true
    }

    PageInfo resolve(String path) {
        return page.resolve(path)
    }

    URI getURI() {
        return page.getURI()
    }

    Collection<Link> getLocalLinks() {
        return []
    }
}

class LinkImpl implements Link {
    private final HtmlAnchor anchor
    private final HtmlUnitBrowser browser
    private final PageInfo target
    private final PageInfo referent

    def LinkImpl(HtmlUnitBrowser browser, HtmlAnchor anchor, PageInfo referent, PageInfo target) {
        this.anchor = anchor
        this.browser = browser
        this.target = target
        this.referent = referent
    }

    def String toString() {
        return anchor.hrefAttribute
    }

    URI getURI() {
        return target.URI
    }

    WebsitePage open() {
        Page targetPage
        try {
            targetPage = anchor.click()
        } catch (RuntimeException e) {
            if (e.cause instanceof FileNotFoundException) {
                throw new PageNotFoundException("Page $target not found for link on page $referent", e);
            }
        }
        if (!(targetPage instanceof HtmlPage)) {
            return new BinaryWebsitePage(target)
        }
        return new WebsitePageImpl(browser, targetPage, target)
    }
}
