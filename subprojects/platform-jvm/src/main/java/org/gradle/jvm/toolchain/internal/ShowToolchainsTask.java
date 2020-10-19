/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.Project;
import org.gradle.api.tasks.diagnostics.AbstractReportTask;
import org.gradle.api.tasks.diagnostics.internal.ReportRenderer;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ShowToolchainsTask extends AbstractReportTask {

    static class ReportableToolchain {

        JavaInstallationProbe.ProbeResult probe;

        InstallationLocation location;

        public ReportableToolchain(JavaInstallationProbe.ProbeResult probe, InstallationLocation location) {
            this.probe = probe;
            this.location = location;
        }

    }
    private final ToolchainReportRenderer toolchainRenderer = new ToolchainReportRenderer();

    @Override
    protected void generate(Project project) throws IOException {
        allReportableToolchains().forEach(toolchainRenderer::printToolchain);
    }

    private List<ReportableToolchain> allReportableToolchains() {
        return getInstallationRegistry().listInstallations().stream()
            .map(this::asReportableToolchain)
            .sorted(reportingProbeCompator())
            .collect(Collectors.toList());
    }

    private ReportableToolchain asReportableToolchain(InstallationLocation location) {
        final JavaInstallationProbe.ProbeResult result = getProbeService().checkJdk(location.getLocation());
        return new ReportableToolchain(result, location);
    }

    private Comparator<ReportableToolchain> reportingProbeCompator() {
        return Comparator
            .<ReportableToolchain, String>comparing(t -> t.probe.getImplementationName())
            .thenComparing(t -> t.probe.getJavaVersion());
    }

    @Inject
    protected SharedJavaInstallationRegistry getInstallationRegistry() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected JavaInstallationProbe getProbeService() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ReportRenderer getRenderer() {
        return toolchainRenderer;
    }

}
