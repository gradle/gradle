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
        String enrichedDesc = data.description + buildFixAllButton(currentApiChanges) + buildAutoSelectSeverityFilter()
        return new RichReportData(data.reportTitle, enrichedDesc, data.violations)
    }

    private static String buildFixAllButton(String currentApiChanges) {
        // language=javascript
        return """
            <script type="text/javascript">
                function getAllErrorCorrections() {
                    var changeElements = \$(".well pre");
                    var result = [];
                    changeElements.each((idx, val) => result.push(JSON.parse(val.textContent)));
                    return result;
                }

                function appendErrorCorrections(reason) {
                    var result = JSON.parse('${currentApiChanges.replace('\n', '')}'); // JSON string from report uses double quotes, contain it within single quotes
                    getAllErrorCorrections().forEach((correction) => {
                        correction.acceptation = reason;
                        result.acceptedApiChanges.push(correction);
                    });
                    // Sort the array in place by type, then member
                    // Note that Firefox is fine with a sort function returning any positive or negative number, but Chrome 
                    // requires 1 or -1 specifically and ignores higher or lower values.  This sort ought to remain consistent
                    // with the sort used by AbstractAcceptedApiChangesMaintenanceTask.
                    result.acceptedApiChanges.sort((a, b) => { 
                        if ((a.type +'#' + a.member) > (b.type + '#' + b.member)) {
                            return 1; 
                        } else {
                            return -1;
                        }
                    });
                    // Remove duplicates (equal adjacent elements) - a new, un@Incubating type will be here twice, as 2 errors are reported; use stringified JSON to compare
                    // Filtering an array is NOT in place
                    result.acceptedApiChanges = result.acceptedApiChanges.filter((item, pos, ary) => (!pos || (JSON.stringify(item) != JSON.stringify(ary[pos - 1]))));
                    return result;
                }

                function acceptAllErrorCorrections() {
                    var reason = prompt("Enter a reason for accepting these changes:");
                    if (!reason) {
                        alert("You must enter a reason to accept all changes.");
                        return;
                    }

                    var textToWrite = JSON.stringify(appendErrorCorrections(reason), null, 4) + "\\n";
                    var textFileAsBlob = new Blob([textToWrite], {type:'text/plain'});
                    var fileNameToSaveAs = 'accepted-public-api-changes.json';

                    var downloadLink = document.createElement("a");
                    downloadLink.download = fileNameToSaveAs;
                    downloadLink.innerHTML = "Download File";

                    // Add the link to the DOM so that it can be clicked
                    downloadLink.href = window.URL.createObjectURL(textFileAsBlob);
                    downloadLink.style.display = "none";
                    document.body.appendChild(downloadLink);

                    downloadLink.click();
                }
            </script>
            <a class="btn btn-info" role="button" onclick="acceptAllErrorCorrections()">Accept Changes for all Errors</a>
        """
    }

    /**
     * Since jQuery isn't included until the bottom of this report, we need to delay until the DOM is ready using vanilla
     * javascript before doing anything.  Then we need to add a function to run on ready, which will run after the report's
     * own javascript based filtering logic is attached with jQuery.
     */
    private static String buildAutoSelectSeverityFilter() {
        // language=javascript
        return """
            <script type="text/javascript">
                document.addEventListener("DOMContentLoaded", function(event) {
                    \$(document).ready(function () {
                        const level = \$("#filter-preset")[0].value;
                        \$("a[role='menuitem']").each (function() {
                            if (this.text === level) {
                                this.click();
                            }
                        });

                        var divider = \$("<hr>");
                        divider.css({ margin: "5px" });
                        var tip = \$("<small>").text("Use the 'bin.cmp.report.severity.filter' property to set the default severity filter");
                        tip.css({ padding: "20px" });
                        var menu = \$("ul .dropdown-menu");
                        menu.css({ width: "480px" });
                        menu.append(divider);
                        menu.append(tip);
                    });
                });
            </script>
        """
    }
}
