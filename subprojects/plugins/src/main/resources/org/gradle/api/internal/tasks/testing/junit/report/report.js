var tabs = new Object();

function initTabs() {
    var container = document.getElementById('tabs');
    tabs.tabs = findTabs(container);
    tabs.titles = findTitles(tabs.tabs);
    tabs.headers = findHeaders(container);
    tabs.select = select;
    tabs.deselectAll = deselectAll;
    tabs.select(0);
    return true;
}

window.onload = initTabs;

function switchTab() {
    var id = this.id.substr(1);
    for (var i = 0; i < tabs.tabs.length; i++) {
        if (tabs.tabs[i].id == id) {
            tabs.select(i);
            return false;
        }
    }
    return false;
}

function select(i) {
    this.deselectAll();
    this.tabs[i].setAttribute('class', 'tab selected');
    this.headers[i].setAttribute('class', 'selected');
    while (this.headers[i].firstChild) {
        this.headers[i].removeChild(this.headers[i].firstChild);
    }
    var h2 = document.createElement('H2');
    h2.appendChild(document.createTextNode(this.titles[i]));
    this.headers[i].appendChild(h2);
}

function deselectAll() {
    for (var i = 0; i < this.tabs.length; i++) {
        this.tabs[i].setAttribute('class', 'tab deselected');
        this.headers[i].setAttribute('class', 'deselected');
        while (this.headers[i].firstChild) {
            this.headers[i].removeChild(this.headers[i].firstChild);
        }
        var a = document.createElement('A');
        a.setAttribute('id', 'ltab' + i);
        a.setAttribute('href', '#tab' + i);
        a.onclick = switchTab;
        a.appendChild(document.createTextNode(this.titles[i]));
        this.headers[i].appendChild(a);
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
        if (header.innerText) {
            titles.push(header.innerText)
        } else {
            titles.push(header.textContent)
        }
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
