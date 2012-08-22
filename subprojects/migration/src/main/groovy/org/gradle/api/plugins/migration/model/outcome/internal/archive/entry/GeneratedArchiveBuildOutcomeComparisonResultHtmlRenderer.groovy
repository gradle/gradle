/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.migration.model.outcome.internal.archive.entry

import org.gradle.api.plugins.migration.model.outcome.internal.archive.GeneratedArchiveBuildOutcome
import org.gradle.api.plugins.migration.model.outcome.internal.archive.GeneratedArchiveBuildOutcomeComparisonResult
import org.gradle.api.plugins.migration.model.render.internal.html.BuildOutcomeComparisonResultHtmlRenderer
import org.gradle.api.plugins.migration.model.render.internal.html.HtmlRenderContext

import static org.gradle.api.plugins.migration.model.compare.internal.ComparisonResultType.*

/*
    TODO - missing test coverage
 */
class GeneratedArchiveBuildOutcomeComparisonResultHtmlRenderer extends BuildOutcomeComparisonResultHtmlRenderer<GeneratedArchiveBuildOutcomeComparisonResult> {

    Class<GeneratedArchiveBuildOutcomeComparisonResult> getResultType() {
        return GeneratedArchiveBuildOutcomeComparisonResult.class;
    }

    void render(GeneratedArchiveBuildOutcomeComparisonResult result, HtmlRenderContext context) {
        GeneratedArchiveBuildOutcome from = result.compared.from
        GeneratedArchiveBuildOutcome to = result.compared.to

        if (from.name == to.name) {
            context.render { h3 "Task: “${from.name}”" } // TODO - assuming this is from a task
        } else {
            context.render { h3 "Task: (from: “${from.name}”, to: “${to.name}”)" }
        }

        context.render {
            h4 "Details"
            table {
                tr {
                    th class: "border-right", ""
                    th "Location"
                }
                tr {
                    th class: "border-right no-border-bottom", "From"
                    td from.archiveFile.absolutePath
                }
                tr {
                    th class: "border-right no-border-bottom", "To"
                    td to.archiveFile.absolutePath
                }
            }
        }

        context.render { h4 "Comparison Results" }

        if (result.comparisonResultType == NON_EXISTENT) {
            context.render { p "Neither side produced the archive." }
        } else if (result.comparisonResultType == FROM_ONLY) {
            context.render { p "The archive was only produced on the 'from' side." }
        } else if (result.comparisonResultType == TO_ONLY) {
            context.render { p "The archive was only produced on the 'to' side." }
        } else if (result.comparisonResultType == EQUAL) {
            context.render { p "The archives are completely identical." }
        } else if (result.comparisonResultType == UNEQUAL) {
            renderUnequal(context, result.entryComparisons)
        } else {
            result.comparisonResultType.throwUnsupported()
        }
    }

    private void renderUnequal(HtmlRenderContext context, Iterable<ArchiveEntryComparison> entryComparisons) {
        context.render {
            table {
                tr {
                    th "Path"
                    th "Difference"
                }

                entryComparisons.each { entryComparison ->
                    if (entryComparison.comparisonResultType != EQUAL) {
                        tr {
                            td entryComparison.path
                            td toDifferenceDescription(entryComparison)
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("GroovyMissingReturnStatement")
    protected String toDifferenceDescription(ArchiveEntryComparison entryComparison) {
        switch (entryComparison.comparisonResultType) {
            case FROM_ONLY:
                "from only"
                break
            case TO_ONLY:
                "to only"
                break
            case UNEQUAL:
                toDifferenceDescription(entryComparison.from, entryComparison.to)
                break
            default:
                entryComparison.comparisonResultType.throwUnsupported()
        }
    }

    protected String toDifferenceDescription(ArchiveEntry from, ArchiveEntry to) {
        if (from.directory != to.directory) {
            "from is directory: $from.directory - to is directory: $to.directory"
        } else if (from.size != to.size) {
            "from is $from.size bytes - to is $to.size bytes (${formatSizeDiff(to.size - from.size)})"
        } else if (from.crc != to.crc) {
            "files are same size but with different content"
        } else {
            throw new IllegalStateException("Method was called with equal entries")
        }
    }

    protected String formatSizeDiff(Long diff) {
        (diff > 0 ? "+" : "") + diff.toString()
    }

}
