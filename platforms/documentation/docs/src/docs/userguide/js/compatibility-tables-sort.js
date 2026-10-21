// Copyright (C) 2026 Gradle, Inc.
//
// Licensed under the Creative Commons Attribution-Noncommercial-ShareAlike 4.0 International License.;
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://creativecommons.org/licenses/by-nc-sa/4.0/
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

(function () {
  'use strict';

  // Tables to enhance and the single column (0-indexed) that is sortable.
  // That column is sorted descending on load and can be toggled by clicking its header.
  const TARGETS = [
    { id: 'java-compatibility-table', sortColumn: 2 },
    { id: 'kotlin-compatibility-table', sortColumn: 1 },
    { id: 'groovy-compatibility-table', sortColumn: 1 },
  ];

  class Version {
    constructor(text) {
      this.original = text;
      const match = text.match(/^(\d+)(?:\.(\d+))?(?:\.(\d+))?/);
      this.major = match ? parseInt(match[1], 10) : 0;
      this.minor = match && match[2] ? parseInt(match[2], 10) : 0;
      this.patch = match && match[3] ? parseInt(match[3], 10) : 0;
    }

    compareTo(other) {
      if (this.major !== other.major) return this.major - other.major;
      if (this.minor !== other.minor) return this.minor - other.minor;
      return this.patch - other.patch;
    }

    static tryParse(text) {
      return /^\d+(\.\d+){0,2}/.test(text) ? new Version(text) : null;
    }
  }

  function compareCells(aText, bText) {
    const aVer = Version.tryParse(aText);
    const bVer = Version.tryParse(bText);
    if (aVer && bVer) return aVer.compareTo(bVer);
    return aText.localeCompare(bText, undefined, { numeric: true });
  }

  function enhanceTable(target) {
    const table = document.getElementById(target.id);
    if (!table) return;

    const tbody = table.querySelector('tbody');
    const thead = table.querySelector('thead');
    if (!tbody || !thead) return;

    const header = thead.querySelectorAll('th')[target.sortColumn];
    if (!header) return;

    const columnIndex = target.sortColumn;
    let ascending = false;

    const indicator = document.createElement('span');
    indicator.className = 'sort-indicator';
    indicator.style.fontSize = '0.8em';
    indicator.style.opacity = '0.7';
    header.appendChild(indicator);

    header.style.cursor = 'pointer';
    header.style.userSelect = 'none';
    header.title = 'Click to sort';

    function sort() {
      const rows = Array.from(tbody.querySelectorAll('tr'));
      rows.sort((a, b) => {
        const aCell = a.querySelectorAll('td')[columnIndex];
        const bCell = b.querySelectorAll('td')[columnIndex];
        if (!aCell || !bCell) return 0;
        const cmp = compareCells(aCell.textContent.trim(), bCell.textContent.trim());
        return ascending ? cmp : -cmp;
      });
      rows.forEach(row => tbody.appendChild(row));
      indicator.textContent = ascending ? ' ▲' : ' ▼';
    }

    header.addEventListener('click', () => {
      ascending = !ascending;
      sort();
    });

    // Default: descending (newest at top).
    sort();
  }

  function init() {
    TARGETS.forEach(enhanceTable);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
