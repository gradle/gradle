const enabled = new Map();

// Require Opt-In/User Request/Community PR
enabled.set('includes_todos', ['community', 'tresat']);
enabled.set('javadoc_on_new_files', ['community', 'tresat']);
enabled.set('lacks_tests', ['community', 'tresat']);
enabled.set('summary_table_owners', ['tresat']);
enabled.set('summary_table_platforms', ['community', 'tresat']);

// Require Opt-In/User Request
enabled.set('code_experts', []);

// Always run
enabled.set('add_usual_expert', ['always']);
enabled.set('complex_changes', ['always']);
enabled.set('estimated_time_to_review', ['always']);
enabled.set('misc_labels', ['always']);
enabled.set('platform_labels', ['always']);

/**
 * @module isEnabledAutomation
 * @description Returns true if the given automation should run against the given PR.
 *
 * This is determined by checking if any of the following conditions are met:
 *
 * <ul>
 *     <li>The automation is enabled for all PRs via the presence of 'always' in the appropriate map entry above.</li>
 * </ul>
 *
 * or
 *
 * <ul>
 *     <li>The author is a member of the Gradle organization,</li>
 *     <li>and has opted into the automation by adding their username to the appropriate map entry above.</li>
 * </ul>
 *
 * or
 *
 * <ul>
 *     <li>The author is NOT a member of the Gradle organization,</li>
 *     <li>and the automation is enabled for all community PR authors via the presence of 'community' in the appropriate map entry above.</li>
 * </ul>
 *
 * or
 *
 * <ul>
 *     <li>Someone has commented `@bot-gitstream run <NAME_OF_AUTOMATION>` (the file name, without the `.cm` suffix) on the PR.</li>
 * </ul>
 *
 * @param {string} automationName - The automation name (*.cm file name containing the automation) to check.
 * @param {Map} pr - The gitStream PR instance to check (includes metadata related to the pull request).
 * @returns {boolean} Returns true if the outlined conditions are met, otherwise false.
 * @example {{ 'platform_labels' | isEnabledAutomation(pr) }}
 */
function isEnabledAutomation(automationName, pr) {
    let result;
    const automationActivations = enabled.get(automationName) || [];

    // Check if always enabled, or enabled by comment
    if (automationActivations.includes('always')) {
        result = true;
    } else {
        result = Object.values(pr.comments).some(comment => {
            const checks = extractCheckNames(comment.content);
            if (checks.includes(automationName)) {
                return true;
            }
        });
    }

    // If not found to be enabled by the above checks, check if enabled by user
    if (!result) {
        if (pr.author_is_org_member) {
            result = automationActivations.includes(pr.author);
        } else {
            result = automationActivations.includes('community');
        }
    }

    return result;
}

function extractCheckNames(inputString) {
    const checksPart = inputString.split('@bot-gitstream check');
    if (checksPart.length === 2) {
        const checks = checksPart[1].split(' ');
        const namePattern = /^[a-zA-Z_]+$/;
        return checks.filter(check => namePattern.test(check));
    } else {
        return [];
    }
}

module.exports = isEnabledAutomation;
