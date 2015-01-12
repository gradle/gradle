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

package org.gradle.play.integtest.advanced

import org.gradle.play.integtest.fixtures.PlayMultiVersionRunApplicationIntegrationTest

import static org.gradle.integtests.fixtures.UrlValidator.*

class AdvancedAppContentVerifier {
    static void verifyRunningApp(PlayMultiVersionRunApplicationIntegrationTest test) {
        // Custom Routes
        assert test.playUrl().text.contains("<li>foo:1</li>")
        assert test.playUrl("root").text.contains("<li>bar:2</li>")
        assert test.playUrl("java/one").text.contains("Your new application is ready.")
        assert test.playUrl("scala/one").text.contains("<li>foo:1</li>")

        // Custom Assets
        assertUrlContent test.playUrl("assets/javascripts/test.js"), test.file("app/assets/javascripts/sample.js")
        assertUrlContent test.playUrl("assets/javascripts/sample.js"), test.file("app/assets/javascripts/sample.js")
        assertUrlContent test.playUrl("assets/javascripts/test.min.js"), minifiedSample
        assertUrlContent test.playUrl("assets/javascripts/sample.min.js"), minifiedSample
    }

    static String getMinifiedSample() {
        return "(function(){var c,e,f,b;b=function(a){return a*a};c=[1,2,3,4,5];e={root:Math.sqrt,square:b,cube:function(a){return a*b(a)}};\"undefined\"!==typeof elvis&&null!==elvis&&alert(\"I knew it!\");(function(){var a,b,d;d=[];a=0;for(b=c.length;a<b;a++)f=c[a],d.push(e.cube(f));return d})()}).call(this);"
    }
}
