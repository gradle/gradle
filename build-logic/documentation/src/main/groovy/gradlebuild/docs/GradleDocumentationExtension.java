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

package gradlebuild.docs;

import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * Common root for all Gradle documentation configuration.
 */
public abstract class GradleDocumentationExtension {
    private final ReleaseNotes releaseNotes;
    private final UserManual userManual;
    private final DslReference dslReference;
    private final Javadocs javadocs;
    private final KotlinDslReference kotlinDslReference;

    @Inject
    public GradleDocumentationExtension(ObjectFactory objects) {
        releaseNotes = objects.newInstance(ReleaseNotes.class);
        userManual = objects.newInstance(UserManual.class);
        dslReference = objects.newInstance(DslReference.class);
        javadocs = objects.newInstance(Javadocs.class);
        kotlinDslReference = objects.newInstance(KotlinDslReference.class);
    }

    /**
     * The root directory of all documentation inputs
     */
    public abstract DirectoryProperty getSourceRoot();

    /**
     * Collection of CSS files to include in generated documentation.
     */
    public abstract ConfigurableFileCollection getCssFiles();

    /**
     * The source code to be documented. This should be the "public" APIs.
     */
    public abstract ConfigurableFileCollection getDocumentedSource();

    /**
     * Source code root folders. Java, Groovy & Kotlin public API sources, including
     * generated code.
     */
    public abstract ConfigurableFileCollection getSourceRoots();

    /**
     * Kotlin DSL source code to be documented. This should be the "public" Kotlin APIs,
     * including generated code.
     */
    public abstract ConfigurableFileCollection getKotlinDslSource();

    /**
     * The runtime classpath of the source code to be documented.
     */
    public abstract ConfigurableFileCollection getClasspath();

    /**
     * A working directory to be used to stage documentation as its generated.
     * All of the sections of the documentation have working directories off of this one.
     */
    public abstract DirectoryProperty getStagingRoot();

    /**
     * The final location to place all rendered documentation.
     */
    public abstract DirectoryProperty getDocumentationRenderedRoot();

    /**
     * The collection of rendered documentation.  This is everything laid out as it would be deployed/packaged.
     */
    public abstract ConfigurableFileCollection getRenderedDocumentation();

    // These are all helper methods for configuring the parts of the documentation (DSL ref, javadoc, user manual, etc).
    public ReleaseNotes getReleaseNotes() {
        return releaseNotes;
    }

    public void releaseNotes(Action<? super ReleaseNotes> action) {
        action.execute(releaseNotes);
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

    public KotlinDslReference getKotlinDslReference() {
        return kotlinDslReference;
    }

    public void kotlinDslReference(Action<? super KotlinDslReference> action) {
        action.execute(kotlinDslReference);
    }

    /**
     * This property is wired into very slow documentation generation tasks.
     *
     * Passing -PquickDocs will disable slow documentation tasks.
     *
     */
    public abstract Property<Boolean> getQuickFeedback();
}
