package org.gradle.kotlin.dsl.ktlint.ruleset

import com.github.shyiko.ktlint.core.RuleSet
import com.github.shyiko.ktlint.core.RuleSetProvider
import com.github.shyiko.ktlint.ruleset.standard.FinalNewlineRule
import com.github.shyiko.ktlint.ruleset.standard.IndentationRule
import com.github.shyiko.ktlint.ruleset.standard.MaxLineLengthRule
import com.github.shyiko.ktlint.ruleset.standard.ModifierOrderRule
import com.github.shyiko.ktlint.ruleset.standard.NoBlankLineBeforeRbraceRule
import com.github.shyiko.ktlint.ruleset.standard.NoEmptyClassBodyRule
import com.github.shyiko.ktlint.ruleset.standard.NoLineBreakAfterElseRule
import com.github.shyiko.ktlint.ruleset.standard.NoLineBreakBeforeAssignmentRule
import com.github.shyiko.ktlint.ruleset.standard.NoMultipleSpacesRule
import com.github.shyiko.ktlint.ruleset.standard.NoSemicolonsRule
import com.github.shyiko.ktlint.ruleset.standard.NoTrailingSpacesRule
import com.github.shyiko.ktlint.ruleset.standard.NoUnitReturnRule
import com.github.shyiko.ktlint.ruleset.standard.NoUnusedImportsRule
import com.github.shyiko.ktlint.ruleset.standard.ParameterListWrappingRule
import com.github.shyiko.ktlint.ruleset.standard.SpacingAroundColonRule
import com.github.shyiko.ktlint.ruleset.standard.SpacingAroundCommaRule
import com.github.shyiko.ktlint.ruleset.standard.SpacingAroundCurlyRule
import com.github.shyiko.ktlint.ruleset.standard.SpacingAroundKeywordRule
import com.github.shyiko.ktlint.ruleset.standard.SpacingAroundOperatorsRule
import com.github.shyiko.ktlint.ruleset.standard.SpacingAroundRangeOperatorRule
import com.github.shyiko.ktlint.ruleset.standard.StringTemplateRule


/**
 * Gradle Kotlin DSL ktlint RuleSetProvider.
 *
 * Reuse ktlint-standard-ruleset rules and add custom ones.
 */
class GradleKotlinDslRuleSetProvider : RuleSetProvider {

    override fun get(): RuleSet =
        RuleSet(
            "gradle-kotlin-dsl",

            // ktlint standard ruleset rules --------------------------
            // See https://github.com/shyiko/ktlint/blob/master/ktlint-ruleset-standard/src/main/kotlin/com/github/shyiko/ktlint/ruleset/standard/StandardRuleSetProvider.kt

            // kotlin-dsl: disabled in favor of CustomChainWrappingRule
            // ChainWrappingRule(),
            FinalNewlineRule(),
            // disabled until it's clear how to reconcile difference in Intellij & Android Studio import layout
            // ImportOrderingRule(),
            IndentationRule(),
            MaxLineLengthRule(),
            ModifierOrderRule(),
            NoBlankLineBeforeRbraceRule(),
            // kotlin-dsl disabled in favor of BlankLinesRule
            // NoConsecutiveBlankLinesRule(),
            NoEmptyClassBodyRule(),
            // disabled until it's clear what to do in case of `import _.it`
            // NoItParamInMultilineLambdaRule(),
            NoLineBreakAfterElseRule(),
            NoLineBreakBeforeAssignmentRule(),
            NoMultipleSpacesRule(),
            NoSemicolonsRule(),
            NoTrailingSpacesRule(),
            NoUnitReturnRule(),
            NoUnusedImportsRule(),
            // kotlin-dsl: disabled in favor of CustomImportsRule
            // NoWildcardImportsRule(),
            ParameterListWrappingRule(),
            SpacingAroundColonRule(),
            SpacingAroundCommaRule(),
            SpacingAroundCurlyRule(),
            SpacingAroundKeywordRule(),
            SpacingAroundOperatorsRule(),
            SpacingAroundRangeOperatorRule(),
            StringTemplateRule(),

            // gradle-kotlin-dsl rules --------------------------------

            BlankLinesRule(),
            CustomChainWrappingRule(),
            CustomImportsRule(),
            VisibilityModifiersOwnLineRule(),
            PropertyAccessorOnNewLine()
        )
}
