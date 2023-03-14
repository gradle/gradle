/*
 * Copyright 2023 the original author or authors.
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

import dev.adamko.dokkatoo.DokkatooBasePlugin;
import dev.adamko.dokkatoo.DokkatooExtension;
import dev.adamko.dokkatoo.dokka.parameters.DokkaSourceSetSpec;
import dev.adamko.dokkatoo.formats.DokkatooHtmlPlugin;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jetbrains.dokka.Platform;

public class GradleDokkaPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        GradleDocumentationExtension documentationExtension = project.getExtensions().getByType(GradleDocumentationExtension.class);
        generateDokkaStuff(project, documentationExtension);
    }

    private void generateDokkaStuff(Project project, GradleDocumentationExtension extension) {
        project.getPlugins().apply(DokkatooBasePlugin.class);

        DokkatooExtension dokkatooExtension = project.getExtensions().getByType(DokkatooExtension.class);
        NamedDomainObjectContainer<DokkaSourceSetSpec> sourceSets = dokkatooExtension.getDokkatooSourceSets();
        sourceSets.register("kotlin_dsl", new Action<>() {
            @Override
            public void execute(DokkaSourceSetSpec spec) {
                spec.getDisplayName().set("dsl");
                spec.getSourceRoots().setFrom(extension.getKotlinDslSource());
                spec.getClasspath().setFrom(extension.getClasspath());
                spec.getAnalysisPlatform().set(Platform.jvm);
            }
        });

        project.getPlugins().apply(DokkatooHtmlPlugin.class);
    }

}
