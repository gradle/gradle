(function (window, document) {
    "use strict";

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
        const currentClass = getClassAttribute(element);
        if (currentClass) {
            changeElementClass(element, currentClass.replace(classValue, ""));
        }
    }

    function getCheckBox() {
        return document.getElementById("line-wrapping-toggle");
    }

    function getLabelForCheckBox() {
        return document.getElementById("label-for-line-wrapping-toggle");
    }

    function findCodeBlocks() {
        const codeBlocks = [];
        const tabContainers = getTabContainers();
        for (let i = 0; i < tabContainers.length; i++) {
            const spans = tabContainers[i].getElementsByTagName("span");
            for (let i = 0; i < spans.length; ++i) {
                if (spans[i].className.indexOf("code") >= 0) {
                    codeBlocks.push(spans[i]);
                }
            }
        }
        return codeBlocks;
    }

    function forAllCodeBlocks(operation) {
        const codeBlocks = findCodeBlocks();

        for (let i = 0; i < codeBlocks.length; ++i) {
            operation(codeBlocks[i], "wrapped");
        }
    }

    function toggleLineWrapping() {
        const checkBox = getCheckBox();

        if (checkBox.checked) {
            forAllCodeBlocks(addClass);
        } else {
            forAllCodeBlocks(removeClass);
        }
    }

    function initClipboardCopyButton() {
        document.querySelectorAll(".clipboard-copy-btn").forEach((button) => {
            const copyElementId = button.getAttribute("data-copy-element-id");
            const elementWithCodeToSelect = document.getElementById(copyElementId);

            button.addEventListener("click", () => {
                const text = elementWithCodeToSelect.innerText.trim();
                navigator.clipboard
                    .writeText(text)
                    .then(() => {
                        button.textContent = "Copied!";
                        setTimeout(() => {
                            button.textContent = "Copy";
                        }, 1500);
                    })
                    .catch((err) => {
                        alert("Failed to copy to the clipboard: '" + err.message + "'. Check JavaScript console for more details.")
                        console.warn("Failed to copy to the clipboard", err);
                    });
            });
        });
    }

    class FailureFilter {
        checkbox;
        label;

        constructor() {
            this.checkbox = document.getElementById("failure-filter-toggle");
            this.label = document.getElementById("label-for-failure-filter-toggle");
        }

        hasFailureClass(element) {
            if (!element) {
                return false;
            }
            const className = getClassAttribute(element);
            return className && className.indexOf("failureGroup") >= 0;
        }

        hasFailuresClass(element) {
            if (!element) {
                return false;
            }
            const className = getClassAttribute(element);
            return className && className.indexOf("failures") >= 0;
        }

        isSummaryTab(tabName) {
            return tabName && (tabName.toLowerCase().indexOf("summary") >= 0 ||
                              tabName.toLowerCase().indexOf("aggregated") >= 0);
        }

        hasAnyFailureTabs() {
            const tabContainers = getTabContainers();
            for (let i = 0; i < tabContainers.length; i++) {
                const container = tabContainers[i];
                const headers = findHeaders(container);
                for (let j = 0; j < headers.length; j++) {
                    const link = headers[j].querySelector("a");
                    if (this.hasFailureClass(link)) {
                        return true;
                    }
                }
            }
            return false;
        }

        toggle() {
            const tabContainers = getTabContainers();

            for (let i = 0; i < tabContainers.length; i++) {
                const container = tabContainers[i];

                // Only filter tabs in containers marked with "test-runs-tab" class
                const containerClass = getClassAttribute(container);
                if (!containerClass || containerClass.indexOf("test-runs-tab") < 0) {
                    continue;
                }

                const headers = findHeaders(container);
                const tabs = findTabs(container);
                let firstVisibleTabIndex = -1;

                for (let j = 0; j < headers.length; j++) {
                    const header = headers[j];
                    const tab = tabs[j];
                    const link = header.querySelector("a");

                    // Check if this is a summary tab
                    const isSummary = this.isSummaryTab(link ? link.textContent : "");

                    if (this.checkbox.checked) {
                        // Filter mode: show only tabs with failures or summary tabs
                        if (isSummary || this.hasFailureClass(link)) {
                            removeClass(header, "filtered-out");
                            // Also filter content within the tab (but not for summary tab)
                            if (!isSummary) {
                                this.filterTabContent(tab, true);
                            }
                            if (firstVisibleTabIndex === -1) {
                                firstVisibleTabIndex = j;
                            }
                        } else {
                            // Hide tabs without failures
                            addClass(header, "filtered-out");
                        }
                    } else {
                        // Show all mode
                        removeClass(header, "filtered-out");
                        this.filterTabContent(tab, false);
                        if (firstVisibleTabIndex === -1) {
                            firstVisibleTabIndex = j;
                        }
                    }
                }

                // Auto-select the first visible tab for this container
                // Only click if the tab is not filtered out
                if (firstVisibleTabIndex !== -1 && headers.length > 0) {
                    const header = headers[firstVisibleTabIndex];
                    const headerClass = getClassAttribute(header);
                    if (!headerClass || headerClass.indexOf("filtered-out") < 0) {
                        header.onclick();
                    }
                }
            }
        }

        filterTabContent(tab, showFailuresOnly) {
            // Find all table rows that contain links
            const tables = tab.getElementsByTagName("table");
            for (let i = 0; i < tables.length; i++) {
                const table = tables[i];
                const tableClass = getClassAttribute(table);
                if (!tableClass || tableClass.indexOf("test-results") < 0) {
                    continue;
                }

                const rows = table.getElementsByTagName("tr");
                for (let j = 0; j < rows.length; j++) {
                    const row = rows[j];
                    // Skip header rows
                    if (row.parentNode.tagName.toUpperCase() === "THEAD") {
                        continue;
                    }

                    if (showFailuresOnly) {
                        // Check if any cell in this row has a failure class
                        const cells = row.getElementsByTagName("td");
                        let hasFailure = false;
                        for (let k = 0; k < cells.length; k++) {
                            if (this.hasFailuresClass(cells[k])) {
                                hasFailure = true;
                                break;
                            }
                        }
                        if (hasFailure) {
                            removeClass(row, "filtered-out");
                        } else {
                            addClass(row, "filtered-out");
                        }
                    } else {
                        removeClass(row, "filtered-out");
                    }
                }
            }
        }

        init() {
            if (!this.checkbox || !this.hasAnyFailureTabs()) {
                return false;
            }

            this.checkbox.onclick = () => this.toggle();
            this.checkbox.checked = true;  // Check by default
            removeClass(this.label, "hidden");

            // Automatically apply filtering on page load
            this.toggle();

            return true;
        }
    }

    class FailureSummary {
        link;
        failureFilter;

        constructor(failureFilter) {
            this.link = document.getElementById("failure-summary-link");
            this.failureFilter = failureFilter;
        }

        async loadPageAndFindFailureDetails(url, visited) {
            // Track visited URLs to avoid infinite loops
            if (!visited) {
                visited = {};
            }
            if (visited[url]) {
                return null;
            }
            visited[url] = true;

            try {
                const res = await fetch(url);
                const html = await res.text();
                const doc = new DOMParser().parseFromString(html, "text/html");

                // Check if this page has <div class="result-details"><h3>Failure details</h3>
                const resultDetails = doc.querySelectorAll("div.result-details");
                for (let i = 0; i < resultDetails.length; i++) {
                    const h3 = resultDetails[i].querySelector("h3");
                    if (h3 && h3.textContent.trim() === "Failure details") {
                        // Found the failure details page, extract the h1 title and return both URL and title
                        const h1 = doc.querySelector("h1");
                        const title = h1 ? h1.textContent.trim() : null;
                        return {url: url, title: title};
                    }
                }

                // Not on a failure details page, so recursively navigate from this URL
                // Look for any cells with class="failures" and follow their links
                const failureCells = doc.querySelectorAll("td.failures");
                for (let i = 0; i < failureCells.length; i++) {
                    const links = failureCells[i].getElementsByTagName("a");
                    for (let j = 0; j < links.length; j++) {
                        const href = links[j].getAttribute("href");
                        if (href) {
                            // Resolve relative URL (all links are relative)
                            const baseUrl = url.substring(0, url.lastIndexOf('/') + 1);
                            const fullUrl = baseUrl + href + (href.endsWith(".html") ? "" : "/index.html");
                            const result = await this.loadPageAndFindFailureDetails(fullUrl, visited);
                            if (result) {
                                return result;
                            }
                        }
                    }
                }
            } catch (e) {
                // Silently handle fetch errors
                return null;
            }

            return null;
        }

        async collectFailures() {
            const failures = [];
            const seenFailures = {}; // Track unique failures by their link

            // Sum all failedCount divs for the total
            const failedCountDivs = document.querySelectorAll("div.failedCount");
            let totalFailureCount = 0;
            for (let i = 0; i < failedCountDivs.length; i++) {
                const count = parseInt(failedCountDivs[i].textContent, 10);
                if (!isNaN(count)) {
                    totalFailureCount += count;
                }
            }

            const tabContainers = getTabContainers();

            for (let i = 0; i < tabContainers.length; i++) {
                const container = tabContainers[i];
                const tabs = findTabs(container);
                const headers = findHeaders(container);

                for (let j = 0; j < tabs.length; j++) {
                    const tab = tabs[j];
                    const header = headers[j];
                    const link = header.querySelector("a");
                    const tabName = link ? link.textContent : "Unknown";

                    // Skip summary/aggregated tabs to avoid duplicate failures in list
                    if (this.failureFilter.isSummaryTab(tabName)) {
                        continue;
                    }
                    // Find all table rows with failures
                    const tables = tab.getElementsByTagName("table");
                    for (let k = 0; k < tables.length; k++) {
                        const table = tables[k];
                        const tableClass = getClassAttribute(table);
                        if (!tableClass || tableClass.indexOf("test-results") < 0) {
                            continue;
                        }

                        const rows = table.getElementsByTagName("tr");
                        for (let m = 0; m < rows.length; m++) {
                            const row = rows[m];
                            if (row.parentNode.tagName.toUpperCase() === "THEAD") {
                                continue;
                            }

                            const cells = row.getElementsByTagName("td");

                            // Loop through cells looking for class="failures"
                            for (let n = 0; n < cells.length; n++) {
                                const cellClass = getClassAttribute(cells[n]);
                                if (!cellClass || cellClass.indexOf("failures") < 0) {
                                    continue;
                                }

                                // This cell has failures class, get its link
                                const cellLink = cells[n].querySelector("a");
                                if (!cellLink) {
                                    continue;
                                }

                                const failureLink = cellLink.getAttribute("href");
                                const failureName = cellLink.textContent;

                                if (failureLink) {
                                    // Create unique key for this failure using JSON to avoid collision issues
                                    const failureKey = JSON.stringify([tabName, failureLink]);

                                    // Only add to list if we haven't seen this failure before and under 100
                                    if (!seenFailures[failureKey] && failures.length < 100) {
                                        seenFailures[failureKey] = true;
                                        failures.push({
                                            tab: tabName,
                                            name: failureName,
                                            link: failureLink,
                                            originalLink: failureLink  // Keep original for navigation
                                        });
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Now resolve all failure links to their failure details pages
            const resolvedFailures = [];
            for (let i = 0; i < failures.length; i++) {
                const failure = failures[i];
                const detailsResult = await this.loadPageAndFindFailureDetails(failure.link, {});
                if (detailsResult) {
                    failure.link = detailsResult.url;
                    // Use the h1 title from the failure details page as the display name
                    if (detailsResult.title) {
                        failure.name = detailsResult.title;
                    }
                }
                resolvedFailures.push(failure);
            }

            return {failures: resolvedFailures, total: totalFailureCount};
        }

        async show() {
            const result = await this.collectFailures();
            const failures = result.failures;
            const totalCount = result.total;

            if (totalCount === 0) {
                alert("No failures found.");
                return false;
            }

            let dialogContent = "<div style='position: relative;'>";

            // Close button in top right
            dialogContent += "<button id='close-dialog' style='position: absolute; top: 0; right: 0; padding: 8px 20px; border-radius: 4px; border: 1px solid #333; background-color: #f0f0f0; font-family: sans-serif; font-size: 12pt; cursor: pointer;'>Close</button>";

            dialogContent += "<h2 style='margin-top: 0;'>Failure Summary</h2>";

            // Show count message
            if (totalCount <= 100) {
                dialogContent += "<p>Showing all " + totalCount + " failure" + (totalCount === 1 ? "" : "s") + "</p>";
            } else {
                dialogContent += "<p>Showing first 100 of " + totalCount + " total failures</p>";
            }

            // Add table with header like the main report (no background shading)
            dialogContent += "<table style='width: 100%; border-collapse: collapse;'>";
            dialogContent += "<thead><tr style='border-bottom: 2px solid #333;'>";
            dialogContent += "<th style='text-align: left; padding: 8px; font-weight: bold;'>Test</th>";
            dialogContent += "<th style='text-align: left; padding: 8px; font-weight: bold;'>Gradle Test Run</th>";
            dialogContent += "</tr></thead>";
            dialogContent += "<tbody>";

            for (let i = 0; i < failures.length; i++) {
                const failure = failures[i];
                dialogContent += "<tr style='border-bottom: 1px solid #ddd;'>";
                dialogContent += "<td style='padding: 8px;'>";
                if (failure.link) {
                    dialogContent += "<a href='" + failure.link + "' style='color: #b60808;'>" + failure.name + "</a>";
                } else {
                    dialogContent += "<span style='color: #b60808;'>" + failure.name + "</span>";
                }
                dialogContent += "</td>";
                dialogContent += "<td style='padding: 8px; color: #666;'>" + failure.tab + "</td>";
                dialogContent += "</tr>";
            }

            dialogContent += "</tbody></table>";
            dialogContent += "</div>";

            // Create a modal dialog (25% larger: 1000px/100% width, 875px/87.5% height)
            const dialog = document.createElement("div");
            dialog.style.position = "fixed";
            dialog.style.top = "50%";
            dialog.style.left = "50%";
            dialog.style.transform = "translate(-50%, -50%)";
            dialog.style.backgroundColor = "white";
            dialog.style.border = "2px solid #333";
            dialog.style.padding = "20px";
            dialog.style.zIndex = "10000";
            dialog.style.maxWidth = "1000px";
            dialog.style.width = "100%";
            dialog.style.maxHeight = "875px";
            dialog.style.height = "87.5%";
            dialog.style.overflow = "auto";
            dialog.style.boxShadow = "0 4px 6px rgba(0,0,0,0.3)";

            dialog.innerHTML = dialogContent;

            // Create overlay
            const overlay = document.createElement("div");
            overlay.style.position = "fixed";
            overlay.style.top = "0";
            overlay.style.left = "0";
            overlay.style.width = "100%";
            overlay.style.height = "100%";
            overlay.style.backgroundColor = "rgba(0,0,0,0.5)";
            overlay.style.zIndex = "9999";

            document.body.appendChild(overlay);
            document.body.appendChild(dialog);

            // Add close button handler
            document.getElementById("close-dialog").onclick = function() {
                document.body.removeChild(dialog);
                document.body.removeChild(overlay);
            };

            // Close on overlay click
            overlay.onclick = function() {
                document.body.removeChild(dialog);
                document.body.removeChild(overlay);
            };

            return false;
        }

        init() {
            if (!this.link) {
                return false;
            }

            removeClass(this.link, "hidden");
            this.link.onclick = () => this.show();

            return true;
        }
    }

    function initControls() {
        if (findCodeBlocks().length > 0) {
            const checkBox = getCheckBox();
            const label = getLabelForCheckBox();

            checkBox.onclick = toggleLineWrapping;
            checkBox.checked = false;

            removeClass(label, "hidden");
        }

        // Initialize failure filter and failure summary using classes
        const failureFilter = new FailureFilter();
        if (failureFilter.init()) {
            const failureSummary = new FailureSummary(failureFilter);
            failureSummary.init();
        }

        initClipboardCopyButton()
    }

    class TabManager {
        baseId;
        tabs;
        titles;
        headers;

        constructor(baseId, tabs, titles, headers) {
            this.baseId = baseId;
            this.tabs = tabs;
            this.titles = titles;
            this.headers = headers;
            this.init();
        }

        init() {
            for (let i = 0; i < this.headers.length; i++) {
                const header = this.headers[i];
                header.onclick = () => {
                    this.select(i);
                    return false;
                };
            }
        }

        select(i) {
            this.deselectAll();

            const header = this.headers[i];
            const filtered = this.isFiltered(header);

            // Preserve existing classes like failureGroup, successGroup, skippedGroup
            const link = header.querySelector("a");
            const linkClasses = link ? getClassAttribute(link) : "";
            const preservedClasses = this.getPreservedClasses(linkClasses);

            if (filtered) {
                changeElementClass(header, "selected filtered-out");
            } else {
                changeElementClass(header, "selected");
            }

            // Restore preserved classes to the link
            if (link && preservedClasses) {
                const currentClass = getClassAttribute(link) || "";
                changeElementClass(link, (currentClass + " " + preservedClasses).trim());
            }

            changeElementClass(this.tabs[i], "tab selected");
        }

        deselectAll() {
            for (let i = 0; i < this.tabs.length; i++) {
                const header = this.headers[i];
                const filtered = this.isFiltered(header);

                // Preserve existing classes like failureGroup, successGroup, skippedGroup
                const link = header.querySelector("a");
                const linkClasses = link ? getClassAttribute(link) : "";
                const preservedClasses = this.getPreservedClasses(linkClasses);

                if (filtered) {
                    changeElementClass(header, "deselected filtered-out");
                } else {
                    changeElementClass(header, "deselected");
                }

                // Restore preserved classes to the link
                if (link && preservedClasses) {
                    const currentClass = getClassAttribute(link) || "";
                    changeElementClass(link, (currentClass + " " + preservedClasses).trim());
                }

                changeElementClass(this.tabs[i], "tab deselected");
            }
        }

        getPreservedClasses(classString) {
            if (!classString) {
                return "";
            }
            const classes = [];
            const classList = classString.split(" ");
            for (let i = 0; i < classList.length; i++) {
                const cls = classList[i].trim();
                if (cls === "failureGroup" || cls === "successGroup" || cls === "skippedGroup") {
                    classes.push(cls);
                }
            }
            return classes.join(" ");
        }

        isFiltered(element) {
            const classes = element.getAttribute("class");
            return classes && classes.indexOf("filtered-out") >= 0;
        }
    }

    function getTabContainers() {
        const tabContainers = Array.from(document.getElementsByClassName("tab-container"));

        // Used by existing TabbedPageRenderer users, which have not adjusted to use TabsRenderer yet.
        const legacyContainer = document.getElementById("tabs");
        if (legacyContainer) {
            tabContainers.push(legacyContainer);
        }

        return tabContainers;
    }

    function initTabs() {
        let tabGroups = 0;

        function createTab(num, container) {
            const tabElems = findTabs(container);
            const tabManager = new TabManager("tabs" + num, tabElems, findTitles(tabElems), findHeaders(container));
            tabManager.select(0);
        }

        const tabContainers = getTabContainers();

        for (let i = 0; i < tabContainers.length; i++) {
            createTab(tabGroups, tabContainers[i]);
            tabGroups++;
        }

        return true;
    }

    function findTabs(container) {
        return findChildElements(container, "DIV", "tab");
    }

    function findHeaders(container) {
        const owner = findChildElements(container, "UL", "tabLinks");
        return findChildElements(owner[0], "LI", null);
    }

    function findTitles(tabs) {
        const titles = [];

        for (let i = 0; i < tabs.length; i++) {
            const tab = tabs[i];
            const header = findChildElements(tab, "H2", null)[0];

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
        const elements = [];
        const children = container.childNodes;

        for (let i = 0; i < children.length; i++) {
            const child = children.item(i);

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
        initControls();
    };
} (window, window.document));
