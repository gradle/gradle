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

package org.gradle.play.integtest.samples

import org.gradle.integtests.fixtures.Sample
import org.junit.Rule

import static org.gradle.integtests.fixtures.UrlValidator.*

class AdvancedPlaySampleIntegrationTest extends AbstractPlaySampleIntegrationTest {
    @Rule
    Sample advancedPlaySample = new Sample(temporaryFolder, "play/advanced")

    Sample getPlaySample() {
        return advancedPlaySample
    }

    @Override
    void checkContent() {
        super.checkContent()
        assertUrlContent playUrl("assets/javascripts/sample.js"), appAsset("javascripts/sample.js")
        assertUrlContent playUrl("assets/coffeescript/console.js"), coffeeScriptGeneratedJavaScript
        assertUrlContent playUrl("hello/gradle"), "Hello Gradle!"
        assert playUrl("square").text.contains("Square it!")
        assert playUrl("questions").text.contains("What is your quest?")
    }

    String getCoffeeScriptGeneratedJavaScript() {
        return """(function() {
  console.log("This is coffeescript!");

}).call(this);
"""
    }
}
