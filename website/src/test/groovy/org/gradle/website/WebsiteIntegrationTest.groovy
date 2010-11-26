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

import org.junit.Test
import static org.junit.Assert.*
import org.junit.Rule

class WebsiteIntegrationTest {
    private final Layout layout = new LocalLayout()
    @Rule public final HtmlUnitBrowser browser = new HtmlUnitBrowser(layout)
    
    @Test
    public void brokenLinks() {
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
                        link.probe()
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
    }
}
