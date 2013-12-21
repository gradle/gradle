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

import org.gradle.api.plugins.buildcomparison.render.internal.html.BuildOutcomeHtmlRenderer
import org.gradle.api.plugins.buildcomparison.render.internal.html.HtmlRenderContext

class GeneratedArchiveBuildOutcomeHtmlRenderer extends BuildOutcomeHtmlRenderer<GeneratedArchiveBuildOutcome> {

    final Class<GeneratedArchiveBuildOutcome> outcomeType = GeneratedArchiveBuildOutcome

    void render(GeneratedArchiveBuildOutcome outcome, HtmlRenderContext context) {
        renderTitle(outcome, context)
        context.render {
            table {
                tr {
                    th "Original Location (relative to project root)"
                    th "Archive Copy (relative to this report)"
                }
                tr {
                    td outcome.rootRelativePath
                    if (outcome.archiveFile) {
                        def sourceCopyPath = context.relativePath(outcome.archiveFile)
                        td { a(href: sourceCopyPath, sourceCopyPath) }
                    } else {
                        td "(no file; not created by build)"
                    }
                }
            }
        }
    }
}
