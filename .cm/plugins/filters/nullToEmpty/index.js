/**
 * @module nullToEmpty
 * @description Returns the specified value if non-null, otherwise an empty array.
 * @param {Object} input - The object to investigate
 * @returns Object - The specified value if non-null, otherwise an empty array
 * @example {{ readField('jvm', 'files') | nullToEmpty }}
 */
function nullToEmpty(input) {
    let output;
    if (input) {
        output = input;
    } else {
        output = [];
    }
    console.log("nullToEmpty: " + output);
    return output;
}

module.exports = nullToEmpty;
