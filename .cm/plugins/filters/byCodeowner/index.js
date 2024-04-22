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
    let match = mapping
        .slice()
        .reverse()
        .find(x =>
            ignore()
                .add(x.path)
                .ignores(file)
        );
    if (!match) {
        console.log("No codeowner found for: " + file);
        return ['No owner'];
    }
    return match.owners;
}

module.exports = {
    async: true,
    filter: async (files, pr, token, pathToCodeOwners, callback) => {
        const fileData = await loadCodeownersFile(pr.author, pr.repo, token, pathToCodeOwners);
        console.log("Finished loading codeowners file: " + fileData);
        const mapping = codeownersMapping(fileData);
        console.log("Finished codeowners mapping: " + mapping);

        const result = new Map()
        files.map(f => {
            console.log("Resolving owners for: " + f);
            const owners = resolveCodeowners(mapping, f);
            console.log("Owners for: " + f + " are: " + owners);
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
