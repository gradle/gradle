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


import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

class ProjectInfoPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.tasks.register("projectInfo", ProjectInfoTask)
    }
}

class ProjectInfoTask extends DefaultTask {

    def format = "plain"

    @Option(option = "format", description = "Specify output format. Supported formats: 'plain', 'json'")
    void setFormat(format) {
        this.format = format
    }

    @TaskAction
    void projectInfo() {
        if ("plain".equals(format)) {
            println("$project.name:$project.version")
        } else if ("json".equals(format)) {
            println("""{
    "projectName": "$project.name",
    "version": "$project.version"
}""")
        } else {
            throw new IllegalArgumentException("format must be 'plain' or 'json'")
        }
    }
}
