/**
 * @module readField
 * @description Returns one field for a particular named object in a list of objects.
 * @param {[Object]} objects - The objects to search
 * @param {String} objectName - The name of the particular object to find
 * @param {String} fieldName - The name of the field for the group to return
 * @returns String - The specified value
 * @example {{ files | byPlatform | categorize(branch.diff.files_metadata) | readField('jvm', 'name') }}
 */

function readField(objects, objectName, fieldName) {
    let group = Object.values(objects).find(s => s.name === objectName);
    if (group) {
        const result = group[fieldName];
        if (result === undefined) {
            throw "readField: field '" + fieldName + "' not found on object named '" + objectName + "' with fields '" + Object.keys(group) + "'";
        } else {
            return result;
        }
    } else {
        console.log("readField: named object with field not found '" + objectName + '.' + fieldName + "'");
        return null;
    }
}

module.exports = readField;
