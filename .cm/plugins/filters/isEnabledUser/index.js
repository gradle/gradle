const buildScan = ["wolfs"];
const configuration = ["alllex"];
const devProd = ["blindpirate"];
const execution = [];
const ide = ["hegyibalint", "donat", "reinsch82"];
const jvm = ["big-guy", "ghale", "jvandort", "octylFractal", "tresat"];

/**
 * @module isEnabledUser
 * @description Returns true if the username that is passed to this function is a member of the Gradle BT Team who has opted into gitStream automations.
 * @param {string} Input - The GitHub username to check.
 * @returns {boolean} Returns true if the user is specified in any of the lists of Gradle BT team members above, otherwise false.
 * @example {{ pr.author | isEnabledUser }}
 */
function isEnabledUser(username) {
    return (buildScan.includes(username) || configuration.includes(username) || devProd.includes(username) || execution.includes(username) || ide.includes(username) || jvm.includes(username));
};

module.exports = isEnabledUser;
