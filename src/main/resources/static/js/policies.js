document.addEventListener("DOMContentLoaded", function () {
  const accordionContainer = document.getElementById("accordionContainer");
  const projectName = document.getElementById("projectname").value;
  const templateName = document.getElementById("templateName").value;
  const spinnerOverlay = document.getElementById("spinner-overlay");

  // Fetch existing policies
  async function getPolicies() {
    const existingPolicySelect = document.getElementById("existingPolicy");

    if (projectName) {
      fetch(`/get-existing-policies?projectName=${projectName}`)
        .then((response) => response.json())
        .then((data) => {
          existingPolicySelect.innerHTML =
            '<option value="">-- Select Policy --</option>';
          if (data.length === 0) {
            const option = document.createElement("option");
            option.text = "No existing policies found";
            option.disabled = true;
            existingPolicySelect.add(option);
          } else {
            data.forEach((policy) => {
              const option = document.createElement("option");
              option.value = policy;
              option.text = policy;
              existingPolicySelect.appendChild(option);
            });
          }
        })
        .catch((err) => console.error("Error fetching policies:", err));
    }

    // Auto-add new policy title to select
    document
      .getElementById("newPolicyTitle")
      .addEventListener("input", function () {
        const title = this.value.trim();
        if (!title) return;

        const exists = Array.from(existingPolicySelect.options).some(
          (opt) => opt.value === title
        );
        if (!exists) {
          const newOption = new Option(title, title);
          existingPolicySelect.appendChild(newOption);
          existingPolicySelect.value = title;
        }
      });

    // Fetch details on select change
    existingPolicySelect.addEventListener("change", async function () {
      const selectedPolicy = this.value;
      if (!selectedPolicy) return;
      await fetchAndFillPolicyDetails(projectName, selectedPolicy);
    });
  }

  // Fetch components
  async function fetchComponents() {
    try {
      const res = await fetch(`/policies-components/${projectName}`);
      if (!res.ok) throw new Error("Failed to fetch components");
      const data = await res.json();
      buildAccordion(data);
    } catch (err) {
      console.error("Error fetching components:", err);
    }
  }

  // Build accordion for components
  function buildAccordion(data) {
    accordionContainer.innerHTML = "";

    Object.keys(data).forEach((group) => {
      const item = document.createElement("div");
      item.className = "accordion-item";

      const header = document.createElement("div");
      header.className = "accordion-header";
      header.textContent = group;
      header.addEventListener("click", () =>
        item.classList.toggle("active")
      );

      const content = document.createElement("div");
      content.className = "accordion-content";

      const selectAll = document.createElement("label");
      selectAll.innerHTML = `<input type="checkbox" class="select-all" data-group="${group}"/> Select All`;
      content.appendChild(selectAll);

      data[group].forEach((comp) => {
        const label = document.createElement("label");
        label.innerHTML = `<input type="checkbox" value="${comp.path}"/> ${comp.name}`;
        content.appendChild(document.createElement("br"));
        content.appendChild(label);
      });

      item.appendChild(header);
      item.appendChild(content);
      accordionContainer.appendChild(item);
    });

    // Select all toggle
    accordionContainer.addEventListener("change", (e) => {
      if (e.target.classList.contains("select-all")) {
        const group = e.target.dataset.group;
        const checkboxes = accordionContainer.querySelectorAll(
          ".accordion-content input[type=checkbox]:not(.select-all)"
        );
        checkboxes.forEach((cb) => {
          if (
            cb
              .closest(".accordion-item")
              .querySelector(".accordion-header").textContent === group
          ) {
            cb.checked = e.target.checked;
          }
        });
      }
      updateComponentPath();
    });
  }

  // Update selected paths
 // Update selected groups instead of component paths
 function updateComponentPath() {
   const selectedItems = [];

   document.querySelectorAll(".accordion-item").forEach((item) => {
     const groupName = item.querySelector(".accordion-header").textContent.trim();
     const selectAll = item.querySelector('.select-all');
     const checkboxes = item.querySelectorAll(
       '.accordion-content input[type="checkbox"]:not(.select-all)'
     );

     if (selectAll && selectAll.checked) {
       // Case 1: Select All checked → store group
       selectedItems.push(`group:${groupName}`);
     } else {
       // Case 2: Some individual items checked → store their values
       checkboxes.forEach((cb) => {
         if (cb.checked) {
           selectedItems.push(cb.value);
         }
       });
     }
   });

   document.getElementById("componentPathOutput").textContent =
     `[${selectedItems.join(",")}]`;
 }



  // Add Style Group
  function addStyleGroup() {
    const container = document.getElementById("styleGroups");
    const groupDiv = document.createElement("div");
    groupDiv.className = "style-group";
    groupDiv.innerHTML = `
      <div style="display: flex; justify-content: space-between; align-items: center;">
        <input type="text" class="group-name" placeholder="Style Group Name" required>
         <label><input type="checkbox" class="group-checkbox"> styles can be combined</label>
        <button type="button" class="remove-btn" onclick="this.closest('.style-group').remove()">❌ Remove Group</button>
      </div>

      <div class="styles"></div>
            <button type="button" class="add-btn" onclick="addStyleRow(this)">+ Add Style</button>
    `;
    container.appendChild(groupDiv);
  }

  // Add Style Row
  function addStyleRow(btn, style = { label: "", cls: "", element: "" }) {
    const stylesDiv = btn.parentElement.querySelector(".styles");
    const row = document.createElement("div");
    row.className = "style-row";
    row.innerHTML = `
      <input type="text" placeholder="Style Label" class="style-label" value="${style.label}" required>
      <input type="text" placeholder="CSS Class" class="style-class" value="${style.cls}" required>
      <select class="style-element" required>
        <option value="">--Element --</option>
        <option value="div" ${style.element === "div" ? "selected" : ""}>div</option>
        <option value="section" ${style.element === "section" ? "selected" : ""}>section</option>
        <option value="article" ${style.element === "article" ? "selected" : ""}>article</option>
        <option value="main" ${style.element === "main" ? "selected" : ""}>main</option>
        <option value="aside" ${style.element === "aside" ? "selected" : ""}>aside</option>
        <option value="header" ${style.element === "header" ? "selected" : ""}>header</option>
        <option value="footer" ${style.element === "footer" ? "selected" : ""}>footer</option>
      </select>
      <button type="button" class="remove-btn" onclick="this.parentElement.remove()">❌</button>
    `;
    stylesDiv.appendChild(row);
  }

  // Fetch and fill policy details
  async function fetchAndFillPolicyDetails(projectName, policyTitle) {
    const response = await fetch(
      `/get-policy-details?projectName=${projectName}&policyTitle=${encodeURIComponent(
        policyTitle
      )}`
    );
    if (!response.ok) return;

    const data = await response.json();
    console.log(data);

    document.getElementById("newPolicyTitle").value = data.name || "";
    document.getElementById("componentPathOutput").textContent =
      data.componentPath || "";
    document.getElementById("styleDefaultClasses").value =
      data.styleDefaultClasses || "";
    document.getElementById("styleDefaultElement").value =
      data.styleDefaultElement || "";

    // Component paths
   const paths = data.componentPath
     ? data.componentPath.replace(/[\[\]]/g, "").split(",")
     : [];

   document.querySelectorAll(".accordion-item").forEach((item) => {
     const groupName = `group:${item.querySelector(".accordion-header").textContent.trim()}`;
     const selectAll = item.querySelector(".select-all");
     const checkboxes = item.querySelectorAll(
       '.accordion-content input[type=checkbox]:not(.select-all)'
     );

     if (paths.includes(groupName)) {
       // Case 1: group saved → check "Select All"
       if (selectAll) selectAll.checked = true;
       checkboxes.forEach((cb) => (cb.checked = true));
     } else {
       // Case 2: some individual paths saved → match them
       checkboxes.forEach((cb) => {
         cb.checked = paths.includes(cb.value);
       });
       if (selectAll) {
         selectAll.checked = Array.from(checkboxes).every((cb) => cb.checked);
       }
     }
   });
   updateComponentPath();



    // Style groups
    const container = document.getElementById("styleGroups");
    container.innerHTML = "";

    if (data.styles) {
      Object.entries(data.styles).forEach(([groupName, groupObj]) => {
        const groupDiv = document.createElement("div");
        groupDiv.className = "style-group";
        groupDiv.innerHTML = `
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <input type="text" class="group-name" value="${groupName}" required>
             <label><input type="checkbox" class="group-checkbox" ${
                        groupObj.multiple ? "checked" : ""
                      }> styles can be combined</label>
            <button type="button" class="remove-btn" onclick="this.closest('.style-group').remove()">❌ Remove Group</button>
          </div>

          <button type="button" class="add-btn" onclick="addStyleRow(this)">+ Add Style</button>
          <div class="styles"></div>
        `;

        const stylesDiv = groupDiv.querySelector(".styles");


Object.entries(groupObj.items).forEach(([label, def]) => {
  addStyleRow(
    { parentElement: groupDiv },
    { label, cls: def.cls || "", element: def.element || "div" }
  );
});

        container.appendChild(groupDiv);
      });
    }
  }

  // Submit Policy
  async function submitPolicy() {
    const form = document.getElementById("policyForm");

    const styleGroups = {};
    document.querySelectorAll(".style-group").forEach((group) => {
      const groupName = group.querySelector(".group-name").value.trim();
      if (!groupName) return;

      const styles = {};
     group.querySelectorAll(".style-row").forEach((row) => {
       const label = row.querySelector(".style-label").value.trim();
       const cls = row.querySelector(".style-class").value.trim();
       const element = row.querySelector(".style-element").value.trim();
       if (label && cls) styles[label] = { class: cls, element };
     });


      styleGroups[groupName] = {
        multiple: group.querySelector(".group-checkbox").checked,
        items: styles,
      };
    });

    const componentPath = document.getElementById(
      "componentPathOutput"
    ).textContent;
    const newPolicyTitleInput =
      document.getElementById("newPolicyTitle").value;

    const data = {
      projectName,
      name: newPolicyTitleInput,
      styleDefaultClasses: form.styleDefaultClasses.value,
      styleDefaultElement: form.styleDefaultElement.value,
      componentPath,
      styles: styleGroups,
    };

    try {
      spinnerOverlay.classList.remove("d-none");
      const spinnerStart = Date.now();

      await fetch(
        `/policies/add/${projectName}?templateName=${encodeURIComponent(
          templateName
        )}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(data),
        }
      );

      const elapsed = Date.now() - spinnerStart;
      const remaining = Math.max(0, 5000 - elapsed);

      setTimeout(() => {
        spinnerOverlay.classList.add("d-none");
        window.location.href = `/view/${projectName}`;
      }, remaining);
    } catch (err) {
      spinnerOverlay.classList.add("d-none");
      alert("Error submitting policy: " + err);
    }
  }

  // Expose globally
  window.addStyleGroup = addStyleGroup;
  window.addStyleRow = addStyleRow;
  window.submitPolicy = submitPolicy;

  // Init
  getPolicies();
  fetchComponents();
});