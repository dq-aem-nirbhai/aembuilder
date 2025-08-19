document.addEventListener("DOMContentLoaded", () => {
  const overlay = document.getElementById("loadingOverlay");
  const projectInput = document.getElementById("projectName");
  const packageInput = document.getElementById("packageName");
  const selectedList = document.getElementById("selectedComponents");
  const hiddenInput = document.getElementById("selectedComponentsInput");
  const checkboxes = document.querySelectorAll(".dropdown-section .form-check-input");

  // Show overlay when "Create Project" button is clicked
  document.querySelectorAll(".btn-gradient").forEach(button => {
    button.addEventListener("click", () => {
      overlay.classList.add("active");
      setTimeout(() => {
        overlay.classList.remove("active");
      }, 2000);
    });
  });

  // Auto-generate package name from project name (prevent starting number/symbols except underscore)
  projectInput.addEventListener("input", function () {
    let rawValue = this.value;

    // Remove invalid starting characters (anything not letter/underscore)
    rawValue = rawValue.replace(/^[^a-zA-Z_]+/, "");

    // Remove invalid characters inside (only letters, numbers, underscore allowed)
    rawValue = rawValue.replace(/[^a-zA-Z0-9_]/g, "");

    // Capitalize first letter
    if (rawValue.length > 0) {
      rawValue = rawValue.charAt(0).toUpperCase() + rawValue.slice(1);
    }

    // Capitalize letter after each underscore
    rawValue = rawValue.replace(/_([a-zA-Z])/g, (_, letter) => "_" + letter.toUpperCase());

    this.value = rawValue;

    // Auto-generate package name (lowercase, underscores removed)
    if (rawValue.length > 0) {
      packageInput.value = "com.aem." + rawValue.replace(/_/g, '').toLowerCase();
    } else {
      packageInput.value = "";
    }
  });

  // Component selection logic
  function updateComponentList() {
    selectedList.innerHTML = "";
    const selected = [];

    checkboxes.forEach(cb => {
      if (cb.checked) {
        selected.push(cb.value);
        const li = document.createElement("li");
        li.className = "component-item animate__animated animate__fadeIn";
        li.textContent = cb.value;
        selectedList.appendChild(li);
      }
    });

    if (selected.length === 0) {
      const placeholder = document.createElement("li");
      placeholder.id = "noComponentText";
      placeholder.textContent = "No component selected";
      selectedList.appendChild(placeholder);
    }

    hiddenInput.value = selected.join(",");
  }

  checkboxes.forEach(cb => cb.addEventListener("change", updateComponentList));
});

// Toggle dropdown
function toggleDropdown() {
  document.getElementById("dropdownSection").classList.toggle("show");
}
