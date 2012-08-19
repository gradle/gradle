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

import org.apache.ivy.core.module.descriptor.Artifact;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact;
import org.gradle.internal.Factory;
import org.jmock.Expectations;
import org.jmock.Mockery;

import java.io.File;
import java.util.Collections;

public class ArtifactsTestUtils {
    
    public static DefaultResolvedArtifact createResolvedArtifact(final Mockery context, final String name, final String type, final String extension, final File file) {
        final Artifact artifactStub = context.mock(Artifact.class, "artifact" + name);
        context.checking(new Expectations() {{
            allowing(artifactStub).getName();
            will(returnValue(name));
            allowing(artifactStub).getType();
            will(returnValue(type));
            allowing(artifactStub).getExt();
            will(returnValue(extension));
            allowing(artifactStub).getExtraAttributes();
            will(returnValue(Collections.emptyMap()));
            allowing(artifactStub).getQualifiedExtraAttributes();
            will(returnValue(Collections.emptyMap()));
            allowing(artifactStub).getExtraAttribute(with(org.hamcrest.Matchers.notNullValue(String.class)));
            will(returnValue(null));
        }});
        final Factory artifactSource = context.mock(Factory.class);
        context.checking(new Expectations() {{
            allowing(artifactSource).create();
            will(returnValue(file));
        }});
        final ResolvedDependency resolvedDependency = context.mock(ResolvedDependency.class);
        final ResolvedModuleVersion version = context.mock(ResolvedModuleVersion.class);
        context.checking(new Expectations() {{
            allowing(resolvedDependency).getModule();
            will(returnValue(version));
            allowing(version).getId();
            will(returnValue(new DefaultModuleVersionIdentifier("group", name, "1.2")));
        }});
        return new DefaultResolvedArtifact(resolvedDependency, artifactStub, artifactSource);
    }
    
}