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

package org.gradle.api.file

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files

class FilePropertyJavaInterOpIntegrationTest extends AbstractFilePropertyJavaInterOpIntegrationTest {
    @Override
    void taskDefinition() {
        pluginDir.file("src/main/java/ProducerTask.java") << """
            import ${DefaultTask.name};
            import ${RegularFileProperty.name};
            import ${TaskAction.name};
            import ${OutputFile.name};
            import ${Files.name};
            import ${IOException.name};

            public class ProducerTask extends DefaultTask {
                private final RegularFileProperty outFile = getProject().getObjects().fileProperty();

                @OutputFile
                public RegularFileProperty getOutFile() {
                    return outFile;
                }

                @TaskAction
                public void run() throws IOException {
                    Files.write(outFile.get().getAsFile().toPath(), "content".getBytes());
                }
            }
        """
    }

    @Override
    void taskWithNestedBeanDefinition() {
        pluginDir.file("src/main/java/ProducerTask.java") << """
            import ${DefaultTask.name};
            import ${TaskAction.name};
            import ${Nested.name};
            import ${Files.name};
            import ${IOException.name};

            public class ProducerTask extends DefaultTask {
                private final Params params = getProject().getObjects().newInstance(Params.class);

                @Nested
                public Params getParams() {
                    return params;
                }

                @TaskAction
                public void run() throws IOException {
                    Files.write(params.getOutFile().get().getAsFile().toPath(), "content".getBytes());
                }
            }
        """
    }
}
