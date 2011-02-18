var tabs = new Object();

function initTabs() {
    var container = document.getElementById('tabs');
    tabs.tabs = findTabs(container);
    tabs.titles = findTitles(tabs.tabs);
    tabs.headers = findHeaders(container);
    deselectAll(tabs.tabs, tabs.headers);
    select(tabs.tabs[0], tabs.headers[0]);
    return true;
}

window.onload = initTabs;

function switchTab(id) {
    for (var i = 0; i < tabs.tabs.length; i++) {
        if (tabs.tabs[i].id == id) {
            deselectAll(tabs.tabs, tabs.headers);
            select(tabs.tabs[i], tabs.headers[i]);
            return;
        }
    }
}

function select(tab, header) {
    tab.setAttribute('class', 'tab selected');
    header.setAttribute('class', 'selected');
}

function deselectAll(tabs, headers) {
    for (var i = 0; i < tabs.length; i++) {
        tabs[i].setAttribute('class', 'tab deselected');
        headers[i].setAttribute('class', 'deselected');
    }
}

function findTabs(container) {
    return findChildElements(container, 'DIV', 'tab');
}

function findHeaders(container) {
    var owner = findChildElements(container, 'UL', 'tabLinks');
    return findChildElements(owner[0], 'LI', null);
}

function findTitles(tabs) {
    var titles = new Array();
    for (var i = 0; i < tabs.length; i++) {
        var tab = tabs[i];
        var header = findChildElements(tab, 'H2', null)[0];
        header.parentNode.removeChild(header);
        titles.push(header.textContent)
    }
    return titles;
}

function findChildElements(container, name, targetClass) {
    var elements = new Array();
    var children = container.childNodes;
    for (var i = 0; i < children.length; i++) {
        var child = children.item(i);
        if (child.nodeType == 1 && child.nodeName == name) {
            if (targetClass && child.getAttribute('class').indexOf(targetClass) < 0) {
                continue;
            }
            elements.push(child);
        }
    }
    return elements;
}
