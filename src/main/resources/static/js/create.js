document.addEventListener("DOMContentLoaded", () => {
  const overlay = document.getElementById("loadingOverlay");
  const projectInput = document.getElementById("projectName");
  const packageInput = document.getElementById("packageName");
  const selectedList = document.getElementById("selectedComponents");
  const hiddenInput = document.getElementById("selectedComponentsInput");
  const createBtn = document.querySelector("button[type='submit']");
  const nameStatus = document.getElementById("nameStatus");

  let selectedComponents = [];

  // --- Project name formatting & package auto-generation ---
  projectInput.addEventListener("input", function () {
    let rawValue = this.value;
    rawValue = rawValue.replace(/^[^a-zA-Z_]+/, "");
    rawValue = rawValue.replace(/[^a-zA-Z0-9_]/g, "");
    if (rawValue.length > 0) rawValue = rawValue.charAt(0).toUpperCase() + rawValue.slice(1);
    rawValue = rawValue.replace(/_([a-zA-Z])/g, (_, l) => "_" + l.toUpperCase());
    this.value = rawValue;
    packageInput.value = rawValue.length > 0 ? "com.aem." + rawValue.replace(/_/g, '').toLowerCase() : "";
    checkProjectAvailability(this.value);
  });

  // --- Project name availability check ---
  async function checkProjectAvailability(projectName) {
    try {
      const response = await fetch(`/checkProjectName?name=${encodeURIComponent(projectName)}`);
      const result = await response.json();
      if (result.exists) {
        nameStatus.textContent = result.message;
        nameStatus.style.color = "red";
        createBtn.disabled = true;
      } else {
        nameStatus.textContent = result.message;
        nameStatus.style.color = "green";
        createBtn.disabled = false;
      }
    } catch (err) {
      console.error(err);
      nameStatus.textContent = "⚠️ Error checking availability";
      nameStatus.style.color = "orange";
      createBtn.disabled = true;
    }
  }

  // --- Update displayed components ---
  function updateComponentList() {
    selectedList.innerHTML = "";
    if (selectedComponents.length === 0) {
      const placeholder = document.createElement("li");
      placeholder.id = "noComponentText";
      placeholder.textContent = "No component selected";
      selectedList.appendChild(placeholder);
    } else {
      selectedComponents.forEach(c => {
        const li = document.createElement("li");
        li.className = "component-item animate__animated animate__fadeIn";
        li.textContent = c;
        selectedList.appendChild(li);
      });
    }
    hiddenInput.value = selectedComponents.join(",");
  }

  function getProjectName() {
    return projectInput.value.trim();
  }

  // --- Render component list in modal ---
  function renderList(containerId, dataList, selectedListArray) {
    const container = document.getElementById(containerId);
    if (!container) return;
    container.innerHTML = '';

    const unique = dataList.unique || [];
    const duplicate = dataList.duplicate || [];
    const allItems = [...new Set([...unique, ...duplicate, ...selectedListArray])];

    allItems.forEach(item => {
      const isAlreadyAdded = selectedListArray.includes(item);
      const isDuplicate = duplicate.includes(item);

      let labelSuffix = '';
      let isDisabled = false;
      let isChecked = false;

      if (isAlreadyAdded) {
        labelSuffix = ' (Already Added)';
        isDisabled = true;
        isChecked = true;
      } else if (isDuplicate) {
        labelSuffix = ' (Exists)';
        isDisabled = true;
      }

      container.insertAdjacentHTML('beforeend', `
        <div class="col">
          <div class="form-check">
            <input class="form-check-input" type="checkbox" value="${item}" ${isChecked ? 'checked' : ''} ${isDisabled ? 'disabled' : ''}>
            <label class="form-check-label ${isDisabled ? 'text-muted' : ''}">${item}${labelSuffix}</label>
          </div>
        </div>`);
    });
  }

  // --- Open component modal ---
  window.openComponentModal = function() {
    const projectName = getProjectName();
    fetch(`/fetch-components/${projectName}`)
      .then(res => {
        if (!res.ok) throw new Error('Failed to fetch components');
        return res.json();
      })
      .then(data => renderList('componentList', data, selectedComponents))
      .then(() => new bootstrap.Modal(document.getElementById('componentModal')).show())
      .catch(err => alert('Unable to load components. Please try again.'));
  };

  // --- Add selected components from modal ---
  window.addSelectedComponents = function() {
    const checkboxes = document.querySelectorAll("#componentList input[type=checkbox]:checked:not(:disabled)");
    checkboxes.forEach(cb => {
      if (!selectedComponents.includes(cb.value)) selectedComponents.push(cb.value);
    });
    updateComponentList();
    const modalEl = document.getElementById("componentModal");
    bootstrap.Modal.getInstance(modalEl).hide();
  };

  // --- Toggle overlay spinner on form submit ---
  const form = document.querySelector("form");
  form.addEventListener("submit", () => {
    overlay.classList.add("active");
    createBtn.disabled = true;
  });
});
