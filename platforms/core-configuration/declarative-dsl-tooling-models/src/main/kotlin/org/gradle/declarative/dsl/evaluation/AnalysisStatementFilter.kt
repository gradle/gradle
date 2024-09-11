/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.declarative.dsl.evaluation

import org.gradle.declarative.dsl.evaluation.AnalysisStatementFilter.AnalyzeEverythingFilter
import org.gradle.declarative.dsl.evaluation.AnalysisStatementFilter.CompositionFilter
import org.gradle.declarative.dsl.evaluation.AnalysisStatementFilter.CompositionFilter.AndFilter
import org.gradle.declarative.dsl.evaluation.AnalysisStatementFilter.CompositionFilter.OrFilter
import org.gradle.declarative.dsl.evaluation.AnalysisStatementFilter.ConfiguringCallFilter
import org.gradle.declarative.dsl.evaluation.AnalysisStatementFilter.NamedCallFilter
import org.gradle.declarative.dsl.evaluation.AnalysisStatementFilter.NotFilter
import org.gradle.declarative.dsl.evaluation.AnalysisStatementFilter.TopLevelElementFilter
import org.gradle.tooling.ToolingModelContract
import java.io.Serializable


@ToolingModelContract(
    subTypes = [
        CompositionFilter::class,
        AndFilter::class,
        OrFilter::class,
        NotFilter::class,
        ConfiguringCallFilter::class,
        TopLevelElementFilter::class,
        NamedCallFilter::class,
        AnalyzeEverythingFilter::class,
    ]
)
sealed interface AnalysisStatementFilter : Serializable {
    @ToolingModelContract(
        subTypes = [
            AndFilter::class,
            OrFilter::class,
        ]
    )
    sealed interface CompositionFilter : AnalysisStatementFilter {
        val left: AnalysisStatementFilter
        val right: AnalysisStatementFilter

        interface AndFilter : CompositionFilter
        interface OrFilter : CompositionFilter
    }

    interface NotFilter : AnalysisStatementFilter {
        val negationOf: AnalysisStatementFilter
    }

    interface ConfiguringCallFilter : AnalysisStatementFilter
    interface TopLevelElementFilter : AnalysisStatementFilter
    interface NamedCallFilter : AnalysisStatementFilter {
        val callName: String
    }

    interface AnalyzeEverythingFilter : AnalysisStatementFilter
}
