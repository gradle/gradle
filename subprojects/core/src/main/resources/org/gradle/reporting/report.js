(function (window, document) {
    "use strict";

    var GRADLE_LINE_WRAPPING_KEY = "gradle.line-wrapping";

    var tabs = {};
    var storage = false;

    function changeElementClass(element, classValue) {
        if (element.getAttribute("className")) {
            element.setAttribute("className", classValue);
        } else {
            element.setAttribute("class", classValue);
        }
    }

    function getClassAttribute(element) {
        if (element.getAttribute("className")) {
            return element.getAttribute("className");
        } else {
            return element.getAttribute("class");
        }
    }

    function addClass(element, classValue) {
        changeElementClass(element, getClassAttribute(element) + " " + classValue);
    }

    function removeClass(element, classValue) {
        changeElementClass(element, getClassAttribute(element).replace(classValue, ""));
    }

    function initTabs() {
        var container = document.getElementById("tabs");

        tabs.tabs = findTabs(container);
        tabs.titles = findTitles(tabs.tabs);
        tabs.headers = findHeaders(container);
        tabs.select = select;
        tabs.deselectAll = deselectAll;
        tabs.select(0);

        return true;
    }

    function getCheckBox() {
        return document.getElementById("line-wrapping-toggle");
    }

    function getLabelForCheckBox() {
        return document.getElementById("label-for-line-wrapping-toggle");
    }

    function checkIsStorageAvailable() {
        try {
            var key = "testField";
            var expected = "true";
            var actual;

            window.localStorage.setItem(key, expected);
            actual = window.localStorage.getItem(key);
            window.localStorage.removeItem(key);

            return expected === actual;
        } catch (exception) {
            return false;
        }
    }

    function storeState(state) {
        if (storage) {
            storage.setItem(GRADLE_LINE_WRAPPING_KEY, state.toString());
        }
    }

    function getState() {
        if (storage) {
            return storage.getItem(GRADLE_LINE_WRAPPING_KEY) === "true";
        }

        return false;
    }

    function initializeFromStorage() {
        var checkBox = getCheckBox();
        var state = getState();

        if (state) {
            checkBox.checked = true;

            storeState(true);
            forAllCodeBlocks(addClass);
        } else {
            checkBox.checked = false;

            storeState(false);
            forAllCodeBlocks(removeClass);
        }
    }

    function initStorage() {
        if ("localStorage" in window) {
            if (checkIsStorageAvailable()) {
                storage = window.localStorage;
                initializeFromStorage();
            }
        }
    }

    function forAllCodeBlocks(operation) {
        var codeBlocks;
        var i;

        codeBlocks = document.getElementById("tabs").getElementsByTagName("span");

        for (i = 0; i < codeBlocks.length; ++i) {
            operation(codeBlocks[i], "wrapped");
        }
    }

    function toggleLineWrapping() {
        var checkBox = getCheckBox();

        if (checkBox.checked) {
            forAllCodeBlocks(addClass);
            storeState(true);
        } else {
            forAllCodeBlocks(removeClass);
            storeState(false);
        }
    }

    function initControls() {
        if (storage) {
            var checkBox = getCheckBox();
            var label = getLabelForCheckBox();

            checkBox.onclick = toggleLineWrapping;
            removeClass(label, "hidden");
        }
    }

    function switchTab() {
        var id = this.id.substr(1);

        for (var i = 0; i < tabs.tabs.length; i++) {
            if (tabs.tabs[i].id === id) {
                tabs.select(i);
                break;
            }
        }

        return false;
    }

    function select(i) {
        this.deselectAll();

        changeElementClass(this.tabs[i], "tab selected");
        changeElementClass(this.headers[i], "selected");

        while (this.headers[i].firstChild) {
            this.headers[i].removeChild(this.headers[i].firstChild);
        }

        var h2 = document.createElement("H2");

        h2.appendChild(document.createTextNode(this.titles[i]));
        this.headers[i].appendChild(h2);
    }

    function deselectAll() {
        for (var i = 0; i < this.tabs.length; i++) {
            changeElementClass(this.tabs[i], "tab deselected");
            changeElementClass(this.headers[i], "deselected");

            while (this.headers[i].firstChild) {
                this.headers[i].removeChild(this.headers[i].firstChild);
            }

            var a = document.createElement("A");

            a.setAttribute("id", "ltab" + i);
            a.setAttribute("href", "#tab" + i);
            a.onclick = switchTab;
            a.appendChild(document.createTextNode(this.titles[i]));

            this.headers[i].appendChild(a);
        }
    }

    function findTabs(container) {
        return findChildElements(container, "DIV", "tab");
    }

    function findHeaders(container) {
        var owner = findChildElements(container, "UL", "tabLinks");
        return findChildElements(owner[0], "LI", null);
    }

    function findTitles(tabs) {
        var titles = [];

        for (var i = 0; i < tabs.length; i++) {
            var tab = tabs[i];
            var header = findChildElements(tab, "H2", null)[0];

            header.parentNode.removeChild(header);

            if (header.innerText) {
                titles.push(header.innerText);
            } else {
                titles.push(header.textContent);
            }
        }

        return titles;
    }

    function findChildElements(container, name, targetClass) {
        var elements = [];
        var children = container.childNodes;

        for (var i = 0; i < children.length; i++) {
            var child = children.item(i);

            if (child.nodeType === 1 && child.nodeName === name) {
                if (targetClass && child.className.indexOf(targetClass) < 0) {
                    continue;
                }

                elements.push(child);
            }
        }

        return elements;
    }

    // Entry point.

    window.onload = function() {
        initTabs();
        initStorage();
        initControls();
    };
} (window, window.document));