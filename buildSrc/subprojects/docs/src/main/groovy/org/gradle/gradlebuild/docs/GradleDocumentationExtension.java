/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.gradlebuild.docs;

import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;

/**
 * ext {
 *     srcDocsDir = file('src/docs') -> documentationSourceRoot
 *     userguideSrcDir = new File(srcDocsDir, 'userguide')
 *     userguideIntermediateOutputDir = new File(buildDir, 'userguideIntermediate')
 *     userguideSinglePageOutputDir = new File(buildDir, 'userguideSinglePage')
 *
 *     dslSrcDir = new File(srcDocsDir, 'dsl')
 *
 *     docsDir = file("$buildDir/docs") -> documentationRenderedRoot
 *
 *     userguideDir = new File(docsDir, 'userguide')
 *
 *     samplesDir = new File(buildDir, "gradle-samples")
 *     distDocsDir = new File(buildDir, 'distDocs')
 *     docbookSrc = new File(project.buildDir, 'src')
 *     snippetsSrcDir = file('src/snippets')
 *     samplesSrcDir = file('src/samples')
 * }
 */
public abstract class GradleDocumentationExtension {
    private final ReleaseNotes releaseNotes;
    private final ReleaseFeatures releaseFeatures;
    private final UserManual userManual;
    private final DslReference dslReference;
    private final Javadocs javadocs;

    @Inject
    public GradleDocumentationExtension(ObjectFactory objects) {
        releaseNotes = objects.newInstance(ReleaseNotes.class);
        releaseFeatures = objects.newInstance(ReleaseFeatures.class);
        userManual = objects.newInstance(UserManual.class);
        dslReference = objects.newInstance(DslReference.class);
        javadocs = objects.newInstance(Javadocs.class);
    }

    public abstract DirectoryProperty getDocumentationSourceRoot();

    public abstract ConfigurableFileCollection getSource();
    public abstract ConfigurableFileCollection getClasspath();

    public abstract DirectoryProperty getDocumentationRenderedRoot();
    public abstract ConfigurableFileCollection getRenderedDocumentation();

    public ReleaseNotes getReleaseNotes() {
        return releaseNotes;
    }

    public void releaseNotes(Action<? super ReleaseNotes> action) {
        action.execute(releaseNotes);
    }

    public ReleaseFeatures getReleaseFeatures() {
        return releaseFeatures;
    }

    public void releaseFeatures(Action<? super ReleaseFeatures> action) {
        action.execute(releaseFeatures);
    }

    public UserManual getUserManual() {
        return userManual;
    }

    public void userManual(Action<? super UserManual> action) {
        action.execute(userManual);
    }

    public DslReference getDslReference() {
        return dslReference;
    }

    public void dslReference(Action<? super DslReference> action) {
        action.execute(dslReference);
    }

    public Javadocs getJavadocs() {
        return javadocs;
    }

    public void javadocs(Action<? super Javadocs> action) {
        action.execute(javadocs);
    }
}
