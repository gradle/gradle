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

package gradlebuild.docs.dsl.source;

import gradlebuild.docs.dsl.source.model.ClassMetaData;
import gradlebuild.docs.model.SimpleClassMetaDataRepository;
import org.gradle.api.Action;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

class ClassMetaDataUtil {
    static void extractFromMetadata(File metaData, Set<String> excludedPackagePatterns, Action<ClassMetaData> extractor) {
        SimpleClassMetaDataRepository<ClassMetaData> repository = new SimpleClassMetaDataRepository<>();
        repository.load(metaData);

        final Set<String> excludedPrefixes = new HashSet<>();
        final Set<String> excludedPackages = new HashSet<>();
        for (String excludePattern : excludedPackagePatterns) {
            if (excludePattern.endsWith(".**")) {
                String baseName = excludePattern.substring(0, excludePattern.length() - 3);
                excludedPrefixes.add(baseName + '.');
                excludedPackages.add(baseName);
            } else {
                excludedPackages.add(excludePattern);
            }
        }

        repository.each(new Action<ClassMetaData>() {
            @Override
            public void execute(ClassMetaData classMetaData) {
                if (classMetaData.getOuterClassName() != null) {
                    // Ignore inner classes
                    return;
                }
                String packageName = classMetaData.getPackageName();
                if (excludedPackages.contains(packageName)) {
                    return;
                }
                for (String excludedPrefix : excludedPrefixes) {
                    if (packageName.startsWith(excludedPrefix)) {
                        return;
                    }
                }
                extractor.execute(classMetaData);
            }
        });
    }
}
