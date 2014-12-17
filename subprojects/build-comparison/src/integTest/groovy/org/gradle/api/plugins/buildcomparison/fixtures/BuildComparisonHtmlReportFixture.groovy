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

package org.gradle.api.plugins.buildcomparison.fixtures

import org.jsoup.nodes.Document

class BuildComparisonHtmlReportFixture {
    Document document

    public BuildComparisonHtmlReportFixture(Document document) {
        this.document = document
    }

    String getOutcomeName() {
        document.select("h3").text()
    }

    def getEntries() {
        document.select("table")[2].select("tr").tail().collectEntries { [it.select("td")[0].text(), it.select("td")[1].text()] }
    }

    boolean isIdentical() {
        document.select("p").last().text() == "The archives are completely identical."
    }

    def select(def selector) {
        document.select(selector)
    }

    def getResult(String id) {
        def outcomeComparision = document.select("div.build-outcome-comparison").find { it.id() == id }
        outcomeComparision.select(".comparison-result-msg").text()
    }

    String getSourceBuildVersion() {
        document.body().select("table")[0].select("tr")[2].select("td")[0].text()
    }

    String getTargetBuildVersion() {
        document.body().select("table")[0].select("tr")[2].select("td")[1].text()
    }

    def sourceWasInferred(){
        hasInferredHtmlWarning("source")
    }

    def targetWasInferred() {
        hasInferredHtmlWarning("target")
    }

    private void hasInferredHtmlWarning(String buildName) {
        assert document.body().select(".warning.inferred-outcomes").text().contains("Build outcomes were not able to be determined for the $buildName build")
    }
}
