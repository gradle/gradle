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
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

/**
 * Common root for all Gradle documentation configuration.
 */
public interface GradleDocumentationExtension {
    /**
     * The root directory of all documentation inputs
     */
    DirectoryProperty getSourceRoot();

    /**
     * Collection of CSS files to include in generated documentation.
     */
    ConfigurableFileCollection getCssFiles();

    /**
     * The source code to be documented. This should be the "public" APIs.
     */
    ConfigurableFileCollection getDocumentedSource();

    /**
     * Source code root folders. Java, Groovy and Kotlin public API sources, including
     * generated code.
     */
    ConfigurableFileCollection getSourceRoots();

    /**
     * Kotlin DSL source code to be documented. This should be the "public" Kotlin APIs,
     * including generated code.
     */
    ConfigurableFileCollection getKotlinDslSource();

    /**
     * The runtime classpath of the source code to be documented.
     */
    ConfigurableFileCollection getClasspath();

    /**
     * A working directory to be used to stage documentation as its generated.
     * All of the sections of the documentation have working directories off of this one.
     */
    DirectoryProperty getStagingRoot();

    /**
     * The final location to place all rendered documentation.
     */
    DirectoryProperty getDocumentationRenderedRoot();

    /**
     * The collection of rendered documentation.  This is everything laid out as it would be deployed/packaged.
     */
    ConfigurableFileCollection getRenderedDocumentation();

    // These are all helper methods for configuring the parts of the documentation (DSL ref, javadoc, user manual, etc).
    @Nested
    ReleaseNotes getReleaseNotes();

    default void releaseNotes(Action<? super ReleaseNotes> action) {
        action.execute(getReleaseNotes());
    }

    @Nested
    UserManual getUserManual();

    default void userManual(Action<? super UserManual> action) {
        action.execute(getUserManual());
    }

    @Nested
    DslReference getDslReference();

    default void dslReference(Action<? super DslReference> action) {
        action.execute(getDslReference());
    }

    @Nested
    Javadocs getJavadocs();

    default void javadocs(Action<? super Javadocs> action) {
        action.execute(getJavadocs());
    }

    @Nested
    KotlinDslReference getKotlinDslReference();

    default void kotlinDslReference(Action<? super KotlinDslReference> action) {
        action.execute(getKotlinDslReference());
    }

    /**
     * This property is wired into very slow documentation generation tasks.
     *
     * Passing -PquickDocs will disable slow documentation tasks.
     *
     */
    Property<Boolean> getQuickFeedback();

    /**
     * The Gradle version this documentation describes. Used in Asciidoctor attributes
     * (e.g. {@code gradleVersion} and the {@code https://docs.gradle.org/<version>} URLs).
     */
    Property<String> getGradleVersion();

    /**
     * The latest Gradle 8.x patch version, derived from {@code released-versions.json}.
     * Surfaced as the {@code gradleVersion8} Asciidoctor attribute for upgrade-guide back-references.
     */
    Property<String> getGradleVersion8();
}
