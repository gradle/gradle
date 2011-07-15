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

import org.junit.Rule
import org.junit.Test

class WebsiteIntegrationTest {
    private final Layout layout = new LocalLayout()
    @Rule public final HtmlUnitBrowser browser = new HtmlUnitBrowser(layout)

//    Uncomment below to run test against test website
//    @Before public void sysProperties() {
//        System.properties['test.base.uri'] = 'http://www.gradle.org/latest/'
//    }

    //This is a hack, no doubt. However, the link has very little importance and...
    //I spent enough hours trying to figure out why this link is not resolvable from our build machine
    def naughtyLinks = ['www.fcc-fac.ca']

    @Test
    public void brokenLinks() {
        def failedLinks = []
        def homePage = browser.open(layout.homePage())
        def queue = [homePage]
        def visiting = new HashSet()
        while (!queue.empty) {
            def current = queue.remove(0)
            visiting.add(current.URI)
            println "* checking $current"
            for (link in current.links) {
                if (!visiting.add(link.target.URI)) {
                    println "  * seen $link"
                    continue
                }
                if (!link.target.local) {
                    if (link.target.mustExist()) {
                        println "  * probe $link"
                        probe(link, failedLinks)
                    } else {
                        println "  * ignore $link"
                    }
                }
                else {
                    println "  * queue $link"
                    def targetPage = link.open()
                    queue.add(targetPage)
                }
            }
        }
        assert failedLinks.empty : "Some links failed. See the list below:\n" + failedLinks.join("\n") + "\n"
    }

    private def probe(Link link, List<Link> failedLinks) {
        try {
            link.probe()
        } catch (Exception e) {
            if (isNaughtyLink(link)) {
                println "    * probe failed but we will ignore it because this is a naughty link!"
            } else {
                println "    * probe failed!"
                failedLinks << e.message
            }
        }
    }

    private boolean isNaughtyLink(Link link) {
        return naughtyLinks.any { link.target.URI.toString().contains(it) }
    }
}