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

  // Tables to enhance and the column (0-indexed) to sort by on load, descending.
  const TARGETS = [
    { id: 'java-compatibility-table', defaultSortColumn: 0 },
    { id: 'kotlin-compatibility-table', defaultSortColumn: 1 },
    { id: 'groovy-compatibility-table', defaultSortColumn: 1 },
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

    const headerCells = Array.from(thead.querySelectorAll('th'));
    const state = { column: -1, ascending: true };

    function sort(columnIndex, ascending) {
      const rows = Array.from(tbody.querySelectorAll('tr'));
      rows.sort((a, b) => {
        const aCell = a.querySelectorAll('td')[columnIndex];
        const bCell = b.querySelectorAll('td')[columnIndex];
        if (!aCell || !bCell) return 0;
        const cmp = compareCells(aCell.textContent.trim(), bCell.textContent.trim());
        return ascending ? cmp : -cmp;
      });
      rows.forEach(row => tbody.appendChild(row));

      state.column = columnIndex;
      state.ascending = ascending;
      updateIndicators();
    }

    function updateIndicators() {
      headerCells.forEach((header, index) => {
        const indicator = header.querySelector('.sort-indicator');
        if (!indicator) return;
        if (index === state.column) {
          indicator.textContent = state.ascending ? ' ▲' : ' ▼';
        } else {
          indicator.textContent = ' ⇅';
        }
      });
    }

    headerCells.forEach((header, index) => {
      header.style.cursor = 'pointer';
      header.style.userSelect = 'none';
      header.title = 'Click to sort';

      const indicator = document.createElement('span');
      indicator.className = 'sort-indicator';
      indicator.style.fontSize = '0.8em';
      indicator.style.opacity = '0.7';
      indicator.textContent = ' ⇅';
      header.appendChild(indicator);

      header.addEventListener('click', () => {
        const ascending = state.column === index ? !state.ascending : false;
        sort(index, ascending);
      });
    });

    // Default: sort by the configured column, descending (newest at top).
    sort(target.defaultSortColumn, false);
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
