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

package org.gradle.api.plugins.buildcomparison.outcome.internal.archive

import org.gradle.api.plugins.buildcomparison.outcome.internal.archive.entry.ArchiveEntry
import org.gradle.api.plugins.buildcomparison.outcome.internal.archive.entry.ArchiveEntryComparison
import org.gradle.api.plugins.buildcomparison.render.internal.html.BuildOutcomeComparisonResultHtmlRenderer
import org.gradle.api.plugins.buildcomparison.render.internal.html.HtmlRenderContext

import static org.gradle.api.plugins.buildcomparison.compare.internal.ComparisonResultType.*

/*
    TODO - missing test coverage
 */
class GeneratedArchiveBuildOutcomeComparisonResultHtmlRenderer extends BuildOutcomeComparisonResultHtmlRenderer<GeneratedArchiveBuildOutcomeComparisonResult> {

    Class<GeneratedArchiveBuildOutcomeComparisonResult> getResultType() {
        return GeneratedArchiveBuildOutcomeComparisonResult.class;
    }

    void render(GeneratedArchiveBuildOutcomeComparisonResult result, HtmlRenderContext context) {
        renderTitle(result, context)

        def source = result.compared.source
        def target = result.compared.target

        context.render {
            table {
                tr {
                    th class: "border-right", ""
                    th "Original Location (relative to project root)"
                    th "Archive Copy (relative to this report)"
                }
                tr {
                    th class: "border-right no-border-bottom", "Source"
                    td source.rootRelativePath
                    if (source.archiveFile) {
                        def sourceCopyPath = context.relativePath(source.archiveFile)
                        td { a(href: sourceCopyPath, sourceCopyPath) }
                    } else {
                        td "(no file; not created by build)"
                    }
                }
                tr {
                    th class: "border-right no-border-bottom", "Target"
                    td target.rootRelativePath
                    if (target.archiveFile) {
                        def targetCopyPath = context.relativePath(target.archiveFile)
                        td { a(href: targetCopyPath, targetCopyPath) }
                    } else {
                        td "(no file; not created by build)"
                    }
                }
            }
        }

        if (result.comparisonResultType == NON_EXISTENT) {
            resultMsg "Neither side produced the archive.", false, context
        } else if (result.comparisonResultType == SOURCE_ONLY) {
            resultMsg "The archive was only produced by the source build.", false, context
        } else if (result.comparisonResultType == TARGET_ONLY) {
            resultMsg "The archive was only produced by the target build.", false, context
        } else if (result.comparisonResultType == EQUAL) {
            resultMsg "The archives are completely identical.", true, context
        } else if (result.comparisonResultType == UNEQUAL) {
            resultMsg "There are differences within the archive.", false, context
            renderUnequal(context, result.entryComparisons)
        } else {
            result.comparisonResultType.throwUnsupported()
        }
    }

    private resultMsg(String msg, boolean equal, HtmlRenderContext context) {
        context.render { p(class: "${context.equalOrDiffClass(equal)} ${context.comparisonResultMsgClass}", msg) }
    }

    private void renderUnequal(HtmlRenderContext context, Iterable<ArchiveEntryComparison> entryComparisons) {
        context.render {

            h5 "Entry Differences"
            table(class: "archive-entry-differences") {
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
            case SOURCE_ONLY:
                "entry does not exist in target build archive"
                break
            case TARGET_ONLY:
                "entry does not exist in source build archive"
                break
            case UNEQUAL:
                toDifferenceDescription(entryComparison.source, entryComparison.target)
                break
            default:
                entryComparison.comparisonResultType.throwUnsupported()
        }
    }

    protected String toDifferenceDescription(ArchiveEntry source, ArchiveEntry target) {
        if (source.directory != target.directory) {
            if (source.directory) {
                "entry is a directory in the source build and a file in the target build"
            } else {
                "entry is a directory in the target build and a file in the source build"
            }
        } else if (source.size != target.size) {
            "entry in the source build is $source.size bytes - in the target build it is $target.size bytes (${formatSizeDiff(target.size - source.size)})"
        } else if (source.crc != target.crc) {
            "entries are of identical size but have different content"
        } else {
            throw new IllegalStateException("Method was called with equal entries")
        }
    }

    protected String formatSizeDiff(Long diff) {
        (diff > 0 ? "+" : "") + diff.toString()
    }

}
