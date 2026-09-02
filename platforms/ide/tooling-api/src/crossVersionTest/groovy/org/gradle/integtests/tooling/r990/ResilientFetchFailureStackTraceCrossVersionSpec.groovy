/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.tooling.r990

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.r16.CustomModel
import org.gradle.integtests.tooling.r970.FetchFailureTreeAction
import org.gradle.tooling.BuildException
import org.gradle.tooling.IntermediateResultHandler

/**
 * A resilient sync reports a failure per fetched model, so a shared configuration failure would otherwise have its
 * stack trace rendered and sent once per project. From Gradle 9.9 onwards the failures a fetch result carries describe
 * themselves without stack frames; the client still receives the complete failure, frames included, from the
 * {@link BuildException} the build fails with at the end of the sync.
 */
@ToolingApiVersion('>=9.3.0')
@TargetGradleVersion('>=9.9.0')
class ResilientFetchFailureStackTraceCrossVersionSpec extends ToolingApiSpecification {

    private FetchFailureTreeAction.Result fetchResult

    def setup() {
        settingsFile << """
            rootProject.name = 'root'
            include 'a', 'b'
        """
        file('a/build.gradle') << "// intentionally clean\n"
        file('b/build.gradle') << """throw new RuntimeException("FAILURE(:b)")\n"""
        file('init.gradle') << """
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.tooling.provider.model.ToolingModelBuilder
import javax.inject.Inject

gradle.lifecycle.beforeProject {
    it.plugins.apply(CustomPlugin)
}

class CustomModel implements Serializable {
    String getValue() { 'greetings' }
}

class CustomBuilder implements ToolingModelBuilder {
    boolean canBuild(String modelName) {
        return modelName == '${CustomModel.name}'
    }
    Object buildAll(String modelName, Project project) {
        return new CustomModel()
    }
}

class CustomPlugin implements Plugin<Project> {
    @Inject
    CustomPlugin(ToolingModelBuilderRegistry registry) {
        registry.register(new CustomBuilder())
    }

    void apply(Project project) {
    }
}
"""
    }

    def "fetch failures carry no stack frames while the build failure still does"() {
        when:
        fails {
            action()
                .buildFinished(new FetchFailureTreeAction(CustomModel), { fetchResult = it } as IntermediateResultHandler)
                .build()
                .withArguments("--init-script=${file('init.gradle').absolutePath}")
                .run()
        }

        then: "the whole build fails to configure, so every project fails to be queried and the build fails"
        def e = thrown(BuildException)
        fetchResult != null
        fetchResult.failedToQueryProjects.toSet() == ['root', 'a', 'b'] as Set

        and: "no failure description carries a stack frame, nor the elision line that only frames produce"
        fetchResult.rootDescriptionByProject.each { project, description ->
            assert !containsRenderedStackTrace(description): "Unexpected stack trace for project '$project':\n$description"
        }

        and: "the failing project is still named by its own failure, with its cause chain below it"
        fetchResult.failureTreeByProject['b'].message.contains(":b")
        fetchResult.rootDescriptionByProject['b'].contains("Caused by: ")
        fetchResult.rootDescriptionByProject['b'].contains("FAILURE(:b)")

        and: "the same failure reaches the client with its stack trace when the build fails at the end of the sync"
        def cause = collectCauses(e).find { it.message?.contains("FAILURE(:b)") }
        cause != null
        cause.stackTrace.length > 0
    }

    /**
     * Whether the text renders a stack trace: a frame line, or the line that elides a tail of frames shared with the
     * parent. Both are indented, and a suppressed exception's own lines carry one further level of indentation.
     */
    private static boolean containsRenderedStackTrace(String text) {
        return text.readLines().any { it ==~ /\t+(at .+|\.\.\. \d+ more)/ }
    }

    private static List<Throwable> collectCauses(Throwable throwable, int depth = 0) {
        if (throwable == null || depth > 50) {
            return []
        }
        def causes = throwable.respondsTo('getCauses')
            ? throwable.causes
            : (throwable.cause != null ? [throwable.cause] : [])
        return [throwable] + causes.collectMany { collectCauses(it as Throwable, depth + 1) }
    }
}
