/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.artifacts;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.gradle.api.internal.artifacts.*;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.ResolveEngine;
import org.apache.ivy.core.report.ArtifactDownloadReport;

import java.io.File;

public class ArtifactsTestUtils {
    
    public static DefaultResolvedArtifact createResolvedArtifact(Mockery context, final String name, final String type, final String extension, File file) {
        final Artifact artifactStub = context.mock(Artifact.class, "artifact" + name);
        context.checking(new Expectations() {{
            allowing(artifactStub).getName();
            will(returnValue(name));
            allowing(artifactStub).getType();
            will(returnValue(type));
            allowing(artifactStub).getExt();
            will(returnValue(extension));
        }});
        final ResolveEngine resolveEngineMock = context.mock(ResolveEngine.class, "engine" + name);
        final ArtifactDownloadReport artifactDownloadReport = new ArtifactDownloadReport(artifactStub);
        artifactDownloadReport.setLocalFile(file);
        context.checking(new Expectations() {{
            one(resolveEngineMock).download(with(equal(artifactStub)), with(any(DownloadOptions.class)));
            will(returnValue(artifactDownloadReport));
        }});
        return new DefaultResolvedArtifact(artifactStub, resolveEngineMock);
    }
    
}