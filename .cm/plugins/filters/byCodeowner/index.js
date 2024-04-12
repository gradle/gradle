/**
 * @module byCodeowner
 * @description Groups the PR's files' by owner based on the CODEOWNERs file.
 * @param {string[]} files - the gitStream's files context variable
 * @param {Object} pr - the gitStream's pr context variable
 * @param {string} token - access token with repo:read scope, used to read the CODEOWNERS file
 * @param {string} pathToCodeOwners - path from repo root to CODEOWNERS file
 * @returns {Map} - Map from owner name to list of files they own
 * @example {{ files | byCodeowner(pr, env.CODEOWNERS_TOKEN, '.github/CODEOWNERS') }}
 **/

const { Octokit } = require("@octokit/rest");
const ignore = require('./ignore/index.js');

async function loadCodeownersFile(owner, repo, auth, pathToCodeOwners) {
    const octokit = new Octokit({
        request: { fetch },
        auth,
    });

    const res = await octokit.repos.getContent({
        owner,
        repo,
        path: pathToCodeOwners
    });

    return Buffer.from(res.data.content, 'base64').toString()
}

function codeownersMapping(data) {
    return data
        .toString()
        .split('\n')
        .filter(x => x && !x.startsWith('#'))
        .map(x => x.split("#")[0])
        .map(x => {
            const line = x.trim();
            const [path, ...owners] = line.split(/\s+/);
            return {path, owners};
        });
}

function resolveCodeowners(mapping, file) {
    const match = mapping
        .slice()
        .reverse()
        .find(x =>
            ignore()
                .add(x.path)
                .ignores(file)
        );
    if (!match) throw new Error("No codeowner found for ${file}");
    return match.owners;
}

module.exports = {
    async: true,
    filter: async (files, pr, token, pathToCodeOwners, callback) => {
        const fileData = await loadCodeownersFile(pr.author, pr.repo, token, pathToCodeOwners);
        const mapping = codeownersMapping(fileData);

        const result = new Map()
        files.map(f => {
            const owners = resolveCodeowners(mapping, f);
            owners.filter(i => typeof i === 'string')
                .map(u => u.replace(/^@gradle\//, ""))
                .forEach(owner => {
                    if (!result.has(owner)) {
                        result.set(owner, []);
                    }
                    result.get(owner).push(f);
                });
        });

        console.log("byCodeowner: ");
        [...result.keys()].forEach(owner => {
            console.log("[" + owner + ": [" + result.get(owner).join(", ") + "]]");
        });
        return callback(null, result);
    },
}
