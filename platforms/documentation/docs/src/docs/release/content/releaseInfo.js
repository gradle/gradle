function applyNodeMutations(nodeList, operation) {
    [].slice.call(nodeList).forEach(operation);
}

function setText(nodeList, text) {
    applyNodeMutations(nodeList, function (el) {
        el.textContent = text;
    });
}

function formatBuildTime(buildTime) {
    var date = new Date(+buildTime.substring(0, 4), +buildTime.substring(4, 6) - 1, +buildTime.substring(6, 8));
    return ("0" + date.getDate()).slice(-2) + " " + date.toLocaleString("en-us", {month: "short"}) + " " + date.getFullYear();
}

function generateBuildPage(jsonArray) {
    // Nightlies will not have an entry
    if (jsonArray.length !== 1) {
        return
    }
    const json = jsonArray[0];
    setText(document.querySelectorAll(".js-build-time"), ", released " + formatBuildTime(json.buildTime));
    // TODO: We could look up checksums here to embed in the page too
}

document.addEventListener("DOMContentLoaded", function () {
    fetch("https://services.gradle.org/versions/all", {
        method: "get"
    }).then(function (response) {
        return response.json();
    }).then(function (releases) {
        return releases.filter((release) => release.version == "@version@")
    }).then(window.generateBuildPage)
        .catch(function (err) {
            console.error(err);
        });
});
