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

  // Auto-generate package name from project name (prevent starting number)
  projectInput.addEventListener("input", function () {
    let rawValue = this.value;

    // Remove starting numbers
    rawValue = rawValue.replace(/^[0-9]+/, "");

    if (rawValue.length > 0) {
      const capitalized = rawValue.charAt(0).toUpperCase() + rawValue.slice(1);
      this.value = capitalized;
      packageInput.value = "com.aem." + capitalized.replace(/\s+/g, '').toLowerCase();
    } else {
      this.value = "";
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

// --- Project name availability check ---
const nameStatus = document.getElementById("nameStatus");
const createBtn = document.querySelector("button[type='submit']");

async function checkProjectAvailability(projectName) {
  try {
    const response = await fetch(`/checkProjectName?name=${encodeURIComponent(projectName)}`);
    const result = await response.json();

   if (result.exists) {
     nameStatus.textContent = result.message;
     nameStatus.style.color = "red";
     createBtn.disabled = true;
     createBtn.classList.add("disabled-btn");  // 🔹 add style
   } else {
     nameStatus.textContent = result.message;
     nameStatus.style.color = "green";
     createBtn.disabled = false;
     createBtn.classList.remove("disabled-btn"); // 🔹 remove style
   }

  } catch (err) {
    nameStatus.textContent = "⚠️ Error checking availability";
    nameStatus.style.color = "orange";
    createBtn.disabled = true;
  }
}

// hook into project name input
projectInput.addEventListener("input", function () {
  let rawValue = this.value.trim().replace(/^[0-9]+/, "");

  if (rawValue.length > 0) {
    const capitalized = rawValue.charAt(0).toUpperCase() + rawValue.slice(1);
    this.value = capitalized;
    packageInput.value = "com.aem." + capitalized.replace(/\s+/g, '').toLowerCase();

    // 🔹 check backend every time name changes
    checkProjectAvailability(this.value);
  } else {
    packageInput.value = "";
    nameStatus.textContent = "";
    createBtn.disabled = true;
  }
});
