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

package org.gradle.api.tasks.diagnostics.internal.dependencies;

import org.gradle.api.artifacts.ResolveException;
import org.gradle.reporting.ReportRenderer;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;

public final class HtmlResolutionErrorRenderer extends ReportRenderer<List<Throwable>, Writer> {
    @Override
    public void render(List<Throwable> model, Writer output) throws IOException {
        output.write("<html>\n");
        output.write("\t<head>\n");
        output.write("\t\t<title>Resolution Errors</title>\n");
        output.write("\t</head>\n");
        output.write("\t</body>\n");
        model.forEach(e -> writeError(output, e));
        output.write("\t</body>\n");
        output.write("</html>\n");
    }

    private void writeError(Writer output, Throwable e) {
        try {
            output.write("\t\t<h2>" + e.getClass().getSimpleName() + "</h2>\n");
            output.write("\t\t<h3>" + e.getMessage() + "</h3>\n");
            output.write("\t\t<p>");
            e.printStackTrace(new PrintWriter(output));
            output.write("</p>\n");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
