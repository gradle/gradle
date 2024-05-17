/**
 * @module categorize
 * @description Returns a mapping of platforms to the information about the files involved in the PR contained in each platform
 * @param {Map} fileOwners mapping of platform name to a list of files involved in the change located in that platform
 * @param {[FileMetadata]} fileMetadatas - gitStream's list of metadata about file changes in the PR including path
 * @returns {[Object]} Returns a list of objects for each platform containing info about the changes to files in that platform
 * @example {{ owners | categorize(branch.diff.files_metadata) }}
 */

function categorize(fileOwners, fileMetadatas) {
    const result = new Map();
    [...fileOwners.keys()].forEach(platform => {
        result.set(platform, {
            name: platform,
            files: []
        });
    });

    Object.values(fileMetadatas).forEach(fileMetadata => {
        [...fileOwners.keys()].forEach(platform => {
            const files = fileOwners.get(platform);
            if (files.includes(fileMetadata.file)) {
                result.get(platform).files.push(fileMetadata.file);
            }
        });
    });

    console.log("categorize: ");
    console.log([...result.values()]);
    return result;
}

module.exports = categorize;
