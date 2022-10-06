/*
 * Copyright 2022 the original author or authors.
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

package gradlebuild

import me.champeau.gradle.japicmp.report.GroovyReportRenderer
import me.champeau.gradle.japicmp.JApiCmpWorkerAction
import me.champeau.gradle.japicmp.report.RichReportData

class EnrichedReportRenderer extends GroovyReportRenderer {
    private static acceptedChangesRegex = ~/<a href="(.+)">accepted-public-api-changes.json<\/a>/

    @Override
    void render(File htmlReportFile, RichReportData data) {
        super.render(htmlReportFile, enrichReport(data))
    }

    /**
     * This is super-hacky: this report is instantiated via {@code newInstance()}, within a different
     * classloader by {@link JApiCmpWorkerAction}, so there is no way to use a
     * normal property on the renderer instance and just set the location of the API file in it.
     *
     * Instead, we'll encode the path to the file in the description data field, as a link.  This is
     * useful regardless of this renderer's needs, since now there will be a link embedded in the report
     * to quickly open the changes file.  By then we can also regex that path out of the description
     * in order to create a changes {@link File} for further use in this class.
     *
     * @param data the report data containing a description to parse
     * @return accepted api changes reported upon, as a file
     */
    private static File getAcceptedApiChangesFile(RichReportData data) {
        def matcher = data.description =~ acceptedChangesRegex
        return new File(matcher[0][1])
    }

    private static RichReportData enrichReport(RichReportData data) {
        String currentApiChanges = getAcceptedApiChangesFile(data).text
        String enrichedDesc = data.description + buildFixAllButton(currentApiChanges)
        return new RichReportData(data.reportTitle, enrichedDesc, data.violations)
    }

    private static String buildFixAllButton(String currentApiChanges) {
        return """
            <script type="text/javascript">
                function getAllErrorCorrections() {
                    var changeElements = \$(".well pre")
                    var result = []
                    changeElements.each(function() {
                        result.push(JSON.parse(this.textContent));
                    })
                    return result;
                }

                function appendErrorCorrections() {
                    var result = JSON.parse("$currentApiChanges");
                    getAllErrorCorrections().forEach(function(correction) {
                        result.acceptedApiChanges.push(correction);
                    });
                    result.acceptedApiChanges = result.acceptedApiChanges.sort(function(a, b) {
                        return (a.type + a.member) - (b.type + b.member);
                    });
                    return result;
                }

                function acceptAllErrorCorrections() {
                    var textToWrite = JSON.stringify(appendErrorCorrections(), null, 4) + "\\n";
                    var textFileAsBlob = new Blob([textToWrite], {type:'text/plain'});
                    var fileNameToSaveAs = 'accepted-public-api-changes.json';

                    var downloadLink = document.createElement("a");
                    downloadLink.download = fileNameToSaveAs;
                    downloadLink.innerHTML = "Download File";

                    if (window.webkitURL != null) {
                        // Chrome allows the link to be clicked without actually adding it to the DOM
                        downloadLink.href = window.webkitURL.createObjectURL(textFileAsBlob);
                    } else {
                        // Firefox requires the link to be added to the DOM before it can be clicked
                        downloadLink.href = window.URL.createObjectURL(textFileAsBlob);
                        downloadLink.onclick = destroyClickedElement;
                        downloadLink.style.display = "none";
                        document.body.appendChild(downloadLink);
                    }

                    downloadLink.click();
                }
            </script>
            <a class="btn btn-info" role="button" onclick="acceptAllErrorCorrections()">Accept Changes for all Errors</a>
        """
    }
}
