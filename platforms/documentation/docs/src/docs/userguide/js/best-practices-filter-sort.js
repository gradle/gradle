(function() {
  'use strict';
  
  // Wait for the DOM to be fully loaded
  function init() {
    const table = document.getElementById('best-practices-table');
    if (!table) {
      console.error('Best Practices table not found');
      return;
    }
    
    const tbody = table.querySelector('tbody');
    const thead = table.querySelector('thead');
    
    if (!tbody || !thead) {
      console.error('Table body or header not found');
      return;
    }
    
    // Get all data rows (excluding header)
    const allRows = Array.from(tbody.querySelectorAll('tr'));
    
    // Version number type for proper sorting
    class Version {
      constructor(versionString) {
        const parts = versionString.split('.').map(part => parseInt(part, 10));
        this.major = parts[0] || 0;
        this.minor = parts[1] || 0;
        this.revision = parts[2] || 0;
        this.original = versionString;
      }
      
      compareTo(other) {
        if (this.major !== other.major) {
          return this.major - other.major;
        }
        if (this.minor !== other.minor) {
          return this.minor - other.minor;
        }
        return this.revision - other.revision;
      }
      
      static tryParse(text) {
        // Check if text matches version pattern (e.g., "8.14", "9.0.0", "9.1.0")
        if (/^\d+(\.\d+){0,2}$/.test(text)) {
          return new Version(text);
        }
        return null;
      }
    }
    
    // Extract unique sections and versions
    const sections = new Set();
    const versions = new Set();
    
    allRows.forEach(row => {
      const cells = row.querySelectorAll('td');
      if (cells.length >= 3) {
        sections.add(cells[1].textContent.trim());
        versions.add(cells[2].textContent.trim());
      } else {
        console.error('Row has insufficient cells (expected >= 3):', row, 'cells found:', cells.length);
      }
    });
    
    // Populate filter dropdowns
    const sectionFilter = document.getElementById('section-filter');
    const versionFilter = document.getElementById('version-filter');
    
    if (sectionFilter && versionFilter) {
      Array.from(sections).sort().forEach(section => {
        const option = document.createElement('option');
        option.value = section;
        option.textContent = section;
        sectionFilter.appendChild(option);
      });
      
      // Sort versions in descending order (most recent on top)
      Array.from(versions)
        .map(v => new Version(v))
        .sort((a, b) => b.compareTo(a))  // Reverse order for descending
        .forEach(version => {
          const option = document.createElement('option');
          option.value = version.original;
          option.textContent = version.original;
          versionFilter.appendChild(option);
        });
      
      // Add filter event listeners
      sectionFilter.addEventListener('change', applyFilters);
      versionFilter.addEventListener('change', applyFilters);
    }
    
    // Add sorting functionality to header cells
    const headerCells = thead.querySelectorAll('th');
    headerCells.forEach((header, index) => {
      header.style.cursor = 'pointer';
      header.style.userSelect = 'none';
      header.title = 'Click to sort';
      
      // Add a visual indicator
      const indicator = document.createElement('span');
      indicator.style.marginLeft = '5px';
      indicator.style.fontSize = '0.8em';
      indicator.textContent = '⇅';
      header.appendChild(indicator);
      
      let ascending = true;
      header.addEventListener('click', function() {
        sortTable(index, ascending);
        ascending = !ascending;
        
        // Update all indicators
        headerCells.forEach(h => {
          const ind = h.querySelector('span');
          if (ind) ind.textContent = '⇅';
        });
        
        // Update current indicator
        indicator.textContent = ascending ? '▼' : '▲';
      });
    });
    
    // Initial count update
    updateCount();
    
    function applyFilters() {
      const selectedSection = sectionFilter.value;
      const selectedVersion = versionFilter.value;
      
      allRows.forEach(row => {
        const cells = row.querySelectorAll('td');
        if (cells.length >= 3) {
          const section = cells[1].textContent.trim();
          const version = cells[2].textContent.trim();
          
          const sectionMatch = !selectedSection || section === selectedSection;
          const versionMatch = !selectedVersion || version === selectedVersion;
          
          if (sectionMatch && versionMatch) {
            row.style.display = '';
          } else {
            row.style.display = 'none';
          }
        } else {
          console.error('Row has insufficient cells (expected >= 3):', row, 'cells found:', cells.length);
        }
      });
      
      updateCount();
    }
    
    function sortTable(columnIndex, ascending) {
      const visibleRows = allRows.filter(row => row.style.display !== 'none');
      
      visibleRows.sort((a, b) => {
        const aCell = a.querySelectorAll('td')[columnIndex];
        const bCell = b.querySelectorAll('td')[columnIndex];
        
        if (!aCell || !bCell) return 0;
        
        const aText = aCell.textContent.trim();
        const bText = bCell.textContent.trim();
        
        // Try version comparison first
        const aVersion = Version.tryParse(aText);
        const bVersion = Version.tryParse(bText);
        
        if (aVersion && bVersion) {
          const comparison = aVersion.compareTo(bVersion);
          return ascending ? comparison : -comparison;
        }
        
        // Fall back to string comparison
        if (ascending) {
          return aText.localeCompare(bText);
        } else {
          return bText.localeCompare(aText);
        }
      });
      
      // Reorder rows in the table
      visibleRows.forEach(row => tbody.appendChild(row));
    }
    
    function updateCount() {
      const totalCount = allRows.length;
      const visibleCount = allRows.filter(row => row.style.display !== 'none').length;
      const totalElement = document.getElementById('best-practices-total');
      const filteredTextElement = document.getElementById('best-practices-filtered-text');
      
      if (totalElement) {
        totalElement.textContent = totalCount;
      }
      
      if (filteredTextElement) {
        if (visibleCount < totalCount) {
          filteredTextElement.textContent = ` (${visibleCount} displayed)`;
        } else {
          filteredTextElement.textContent = '';
        }
      }
    }
  }
  
  // Run initialization when the page loads
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
