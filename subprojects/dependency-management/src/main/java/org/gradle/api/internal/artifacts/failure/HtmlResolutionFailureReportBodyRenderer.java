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

package org.gradle.api.internal.artifacts.failure;

import org.apache.commons.lang.StringEscapeUtils;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.VersionConflictException;
import org.gradle.internal.Pair;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.locking.LockOutOfDateException;
import org.gradle.internal.resolve.ModuleVersionNotFoundException;
import org.gradle.reporting.ReportRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;

/**
 * Renders the failure report body for a resolution failure.
 *
 * @since 7.5
 */
public final class HtmlResolutionFailureReportBodyRenderer extends ReportRenderer<List<Throwable>, Writer> {
    @Override
    public void render(List<Throwable> model, Writer output) throws IOException {
        String html = buildReportBodyHtml(model);
        output.write(formatHtml(html));
    }

    private String buildReportBodyHtml(List<Throwable> model) {
        StringWriter html = new StringWriter();
        html.append("<div class=\"report\" id=\"report\">");
        model.forEach(e -> writeErrorHtml(html, e));
        html.append("</div>");
        return html.toString();
    }

    private void writeErrorHtml(StringWriter output, Throwable error) {
        output.append("<h3>").append(error.getClass().getName()).append("</h3>");
        output.append("<p>").append(buildResolutionErrorHtml(error)).append("</p>");
        output.append("<p><pre>");
        error.printStackTrace(new PrintWriter(output));
        output.append("</pre></p>");
    }

    private String buildResolutionErrorHtml(Throwable cause) {
        String failureMsg;
        if (cause instanceof VersionConflictException) {
            failureMsg = buildConflictMsg((VersionConflictException) cause);
        } else if (cause instanceof LockOutOfDateException) {
            failureMsg = buildOutOfDateLocksMsg((LockOutOfDateException) cause);
        } else if (cause instanceof ResolveException) {
            failureMsg = buildResolveFailureMsg((ResolveException) cause);
        } else if (cause instanceof ModuleVersionNotFoundException) {
            failureMsg = buildModuleVersionNotFoundMsg((ModuleVersionNotFoundException) cause);
        } else { // Fallback to failing the task in case we don't know anything special about the error
            throw UncheckedException.throwAsUncheckedException(cause);
        }
        return StringEscapeUtils.escapeHtml(failureMsg);
    }

    private String buildModuleVersionNotFoundMsg(ModuleVersionNotFoundException cause) {
        return String.format("Could not find module: %s\n", cause.getSelector().toString());
    }

    private String buildResolveFailureMsg(ResolveException cause) {
        return cause.getMessage();
    }

    private String buildOutOfDateLocksMsg(final LockOutOfDateException cause) {
        StringBuilder msg = new StringBuilder();
        List<String> errors = cause.getErrors();
        msg.append("The dependency locks are out-of-date:\n");
        for (String error : errors) {
            msg.append("   - ").append(error).append('\n');
        }
        msg.append('\n');
        return msg.toString();
    }

    private String buildConflictMsg(final VersionConflictException conflict) {
        StringBuilder msg = new StringBuilder();
        msg.append("Dependency resolution failed because of conflict(s) on the following module(s):\n");
        for (Pair<List<? extends ModuleVersionIdentifier>, String> identifierStringPair : conflict.getConflicts()) {
            msg.append("   - ").append(identifierStringPair.getRight()).append('\n');
        }
        msg.append('\n');
        return msg.toString();
    }

    private String formatHtml(String html) {
        Document body = Jsoup.parseBodyFragment(html);
        body.outputSettings().prettyPrint(true).indentAmount(4);
        return body.body().html();
    }
}
