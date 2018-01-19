/*
 * Copyright 2016 the original author or authors.
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
package org.gradle;

import com.google.common.io.Files;
import org.gradle.api.artifacts.transform.ArtifactTransform;

import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MinifyTransform extends ArtifactTransform {
    private final Map<String, Set<String>> keepClassesByArtifact;

    @Inject
    public MinifyTransform(Map<String, Set<String>> keepClassesByArtifact) {
        this.keepClassesByArtifact = keepClassesByArtifact;
    }

    @Override
    public List<File> transform(File artifact) {
        String name = artifact.getName();
        for (Map.Entry<String, Set<String>> entry : keepClassesByArtifact.entrySet()) {
            if (name.startsWith(entry.getKey())) {
                return Collections.singletonList(minify(artifact, entry.getValue()));
            }
        }
        return Collections.singletonList(artifact);
    }

    private File minify(File artifact, Set<String> keepClasses) {
        File out = getOutputDirectory();
        File jarFile = new File(out, Files.getNameWithoutExtension(artifact.getPath()) + "-min.jar");
        File classesDir = new File(out, "classes");
        File analysisFile = new File(out, "analysis.txt");
        new ShadedJarCreator(Collections.singleton(artifact), jarFile, analysisFile, classesDir, null, keepClasses, keepClasses, new HashSet<String>()).createJar();
        return jarFile;
    }
}
