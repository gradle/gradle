const { execFileSync } = require('child_process')

const LABEL = 'outdated-base'
const REQUIRED_BASE_COMMITS_PATH = '.github/required-base-commits.txt'
const DAY_MILLIS = 24 * 60 * 60 * 1000

function git(...args) {
    return execFileSync('git', args, { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024, stdio: ['ignore', 'pipe', 'pipe'] }).trim()
}

function maxBaseAgeDays() {
    const value = Number(process.env.MAX_BASE_AGE_DAYS)
    if (!Number.isInteger(value) || value <= 0) {
        throw new Error(`MAX_BASE_AGE_DAYS must be a positive integer, got '${process.env.MAX_BASE_AGE_DAYS}'`)
    }
    return value
}

function baseRefName(baseRef) {
    return `refs/remotes/origin/${baseRef}`
}

function readRequiredBaseCommits(baseRef) {
    let content
    try {
        content = git('show', `${baseRefName(baseRef)}:${REQUIRED_BASE_COMMITS_PATH}`)
    } catch (e) {
        return []
    }
    return content
        .split('\n')
        .map(line => line.trim())
        .filter(line => line.length > 0)
        .map(line => {
            const [sha, ...description] = line.split(/\s+/)
            return { sha, description: description.join(' ') }
        })
}

function isAncestor(ancestor, descendant) {
    try {
        execFileSync('git', ['merge-base', '--is-ancestor', ancestor, descendant], { stdio: 'ignore' })
        return true
    } catch (e) {
        if (e.status === 1) {
            return false
        }
        throw e
    }
}

function evaluate(baseRef, head, requiredBaseCommits) {
    const base = baseRefName(baseRef)
    const missingLandings = git('rev-list', '--first-parent', '--reverse', base, `^${head}`).split('\n').filter(sha => sha.length > 0)
    const oldestMissing = missingLandings.length > 0 ? missingLandings[0] : null
    const baseAgeDays = oldestMissing
        ? Math.floor((Date.now() - new Date(git('log', '-1', '--format=%cI', oldestMissing)).getTime()) / DAY_MILLIS)
        : 0

    return {
        baseRef,
        oldestMissing,
        behindBy: Number(git('rev-list', '--count', base, `^${head}`)),
        baseAgeDays,
        tooOld: baseAgeDays > maxBaseAgeDays(),
        missingCommits: requiredBaseCommits.filter(required => !isAncestor(required.sha, head))
    }
}

function commitLink(owner, repo, sha) {
    return `[\`${sha.substring(0, 10)}\`](https://github.com/${owner}/${repo}/commit/${sha})`
}

function describeProblems(owner, repo, result) {
    const problems = []
    if (result.tooOld) {
        problems.push(`The branch is ${result.behindBy} commits behind \`${result.baseRef}\`. It has been missing ${commitLink(owner, repo, result.oldestMissing)} since it landed on \`${result.baseRef}\` ${result.baseAgeDays} days ago. The limit is ${maxBaseAgeDays()} days.`)
    }
    for (const missing of result.missingCommits) {
        const suffix = missing.description ? `: ${missing.description}` : ''
        problems.push(`The branch does not contain the required commit ${commitLink(owner, repo, missing.sha)}${suffix}`)
    }
    return problems
}

async function checkPullRequest({ context, core }) {
    const { owner, repo } = context.repo
    const pr = context.payload.pull_request
    const result = evaluate(pr.base.ref, pr.head.sha, readRequiredBaseCommits(pr.base.ref))

    core.info(`${result.behindBy} commits behind ${result.baseRef}, missing landings since ${result.baseAgeDays} days`)
    const problems = describeProblems(owner, repo, result)
    if (problems.length === 0) {
        return
    }

    await core.summary
        .addHeading(`Outdated \`${result.baseRef}\``, 3)
        .addList(problems)
        .addRaw(`Rebase the branch onto the latest \`${result.baseRef}\`.`)
        .write()

    if (result.tooOld) {
        core.warning(`The branch has been missing commits of ${result.baseRef} for ${result.baseAgeDays} days. Rebase it onto the latest ${result.baseRef}.`)
    }
    if (result.missingCommits.length > 0) {
        core.setFailed(`The branch is missing ${result.missingCommits.length} required commit(s) of ${result.baseRef}. Rebase it onto the latest ${result.baseRef}.`)
    }
}

async function checkOpenPullRequests({ github, context, core }) {
    const { owner, repo } = context.repo
    const pulls = (await github.paginate(github.rest.pulls.list, { owner, repo, state: 'open', per_page: 100 }))
        .filter(pr => !pr.draft)
    if (pulls.length === 0) {
        return
    }

    git('fetch', '--quiet', '--no-tags', '--filter=tree:0', 'origin',
        ...pulls.map(pr => `+refs/pull/${pr.number}/head:refs/remotes/pull/${pr.number}`))

    const requiredByBaseRef = new Map()
    for (const pr of pulls) {
        let result
        try {
            if (!requiredByBaseRef.has(pr.base.ref)) {
                requiredByBaseRef.set(pr.base.ref, readRequiredBaseCommits(pr.base.ref))
            }
            result = evaluate(pr.base.ref, pr.head.sha, requiredByBaseRef.get(pr.base.ref))
        } catch (e) {
            core.warning(`Could not check ${pr.html_url}: ${e.message}`)
            continue
        }

        const problems = describeProblems(owner, repo, result)
        const labelled = pr.labels.some(label => label.name === LABEL)
        if (problems.length > 0 && !labelled) {
            core.info(`Marking ${pr.html_url} as ${LABEL}`)
            await github.rest.issues.addLabels({ owner, repo, issue_number: pr.number, labels: [LABEL] })
            await github.rest.issues.createComment({
                owner, repo, issue_number: pr.number,
                body: [
                    `This pull request is based on an outdated \`${result.baseRef}\`:`,
                    '',
                    ...problems.map(problem => `- ${problem}`),
                    '',
                    `CI failures on this branch may already be fixed on \`${result.baseRef}\`. Please rebase it onto the latest \`${result.baseRef}\`. The \`${LABEL}\` label is removed automatically after the rebase.`
                ].join('\n')
            })
        } else if (problems.length === 0 && labelled) {
            core.info(`Removing ${LABEL} from ${pr.html_url}`)
            await github.rest.issues.removeLabel({ owner, repo, issue_number: pr.number, name: LABEL })
        }
    }
}

module.exports = { checkPullRequest, checkOpenPullRequests }
