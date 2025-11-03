<form id="cfForm" class="coral-Form coral-Form--vertical">
  <div class="form-row">
    <h4 class="coral-Heading coral-Heading--4">Excel File</h4>
    <input
      type="file"
      name="excel"
      id="excelFile"
      accept=".xls,.xlsx"
      class="coral-Textfield"
      placeholder="Select Excel file"
    />
  </div>

  <div class="form-row">
    <h4 class="coral-Heading coral-Heading--4">Content Fragment Parent Path</h4>
    <input
      type="text"
      name="parentPath"
      class="coral-Textfield"
      required
      value="/content/dam/charger"
    />
  </div>

  <div class="form-row">
    <h4 class="coral-Heading coral-Heading--4">Model Type</h4>
    <select
      id="modelTypeDropdown"
      name="modelType"
      class="coral-Textfield"
      required
    >
      <option value="">Loading models...</option>
    </select>
  </div>

  <div class="form-row">
    <h4 class="coral-Heading coral-Heading--4">Content Fragment Name</h4>
    <select
      id="selectfield"
      name="selectfield"
      class="coral-Textfield"
      disabled
    >
      <option value="">Select Excel file to load names...</option>
    </select>
  </div>

  <div class="form-row buttons-row">
    <button type="submit" class="coral-Button coral-Button--primary">Submit</button>
    <button type="button" id="updateBtn" class="coral-Button coral-Button--secondary">Update</button>
  </div>

  <!-- ✅ Inline Message Area -->
  <div id="messageContainer" class="message-container"></div>

  <!-- Loading Spinner -->
  <div id="spinnerOverlay" class="spinner-overlay" style="display:none;">
    <div class="spinner"></div>
    <p>Please wait, processing content fragments...</p>
  </div>

</form>

<style>
  .form-row {
    margin-bottom: 1rem;
  }

  .coral-Heading--4 {
    margin-bottom: 0.5rem;
  }

  #cfForm {
    background-color: #ffffff;
    padding: 2rem;
    border-radius: 8px;
    box-shadow: 0 4px 8px rgba(0,0,0,0.1);
    max-width: 800px;
    margin: 2rem auto;
  }

  input.coral-Textfield,
  select.coral-Textfield {
    width: 50%;
    padding: 0.5rem;
    border: 1px solid #ccc;
    border-radius: 4px;
  }

  .buttons-row {
    display: flex;
    gap: 1rem;
  }

  .buttons-row button {
    flex: 1;
    padding: 0.75rem;
  }

  .coral-Button--primary:hover {
    background-color: #005a9c;
    color: #fff;
  }

  .coral-Button--secondary:hover {
    background-color: #f0f0f0;
  }

  /* ✅ Inline message styles */
  .message-container {
    margin-top: 1rem;
    padding: 1rem;
    border-radius: 6px;
    display: none;
    font-weight: 500;
  }

  .message-container.success {
    background-color: #e6f4ea;
    color: #1e4620;
    border: 1px solid #b7dfb8;
  }

  .message-container.error {
    background-color: #fdecea;
    color: #611a15;
    border: 1px solid #f5c6cb;
  }

  .message-container.info {
    background-color: #e8f0fe;
    color: #174ea6;
    border: 1px solid #c6dafc;
  }

  .message-container.warning {
    background-color: #fff8e1;
    color: #665c00;
    border: 1px solid #ffecb3;
  }

  /* 🔄 Spinner Overlay */
  .spinner-overlay {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background-color: rgba(255, 255, 255, 0.8);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    z-index: 9999;
    font-weight: 500;
    color: #333;
    font-size: 1rem;
  }

  /* The actual spinner circle */
  .spinner {
    border: 6px solid #f3f3f3;
    border-top: 6px solid #005a9c; /* Coral blue */
    border-radius: 50%;
    width: 60px;
    height: 60px;
    animation: spin 1s linear infinite;
    margin-bottom: 1rem;
  }

  @keyframes spin {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
  }

</style>

<script>
document.addEventListener("DOMContentLoaded", () => {
  initForm();
});

/* ---------- Helper Functions ---------- */

// ✅ Show messages inline (instead of alerts)
function showMessage(message, type = "info") {
  const container = document.getElementById("messageContainer");
  container.className = `message-container ${type}`;
  container.innerHTML = message;
  container.style.display = "block";

}

// ✅ Inline confirmation message
function showConfirmation(message, onConfirm, onCancel) {
  const container = document.getElementById("messageContainer");
  container.className = "message-container info";
  container.innerHTML = `
    <p>${message}</p>
    <div style="margin-top:0.5rem;">
      <button id="confirmYes" class="coral-Button coral-Button--primary">Yes</button>
      <button id="confirmNo" class="coral-Button coral-Button--secondary">No</button>
    </div>
  `;
  container.style.display = "block";

  document.getElementById("confirmYes").onclick = () => {
    container.style.display = "none";
    onConfirm();
  };
  document.getElementById("confirmNo").onclick = () => {
    container.style.display = "none";
    if (onCancel) onCancel();
  };
}

/* ---------- Form Initialization ---------- */
async function initForm() {
  await populateDropdown("/bin/getCFModels", "modelTypeDropdown", "No models found");
  document.getElementById("cfForm").addEventListener("submit", handleSubmit);
  document.getElementById("excelFile").addEventListener("change", handleExcelUpload);
}

/* ---------- Dropdown Loading ---------- */
async function populateDropdown(url, dropdownId, emptyMessage) {
  const dropdown = document.getElementById(dropdownId);
  dropdown.disabled = true;
  dropdown.innerHTML = `<option>Loading...</option>`;

  try {
    const response = await fetch(url);
    if (!response.ok) throw new Error(`HTTP error ${response.status}`);
    const items = await response.json();
    dropdown.innerHTML = "";

    if (Array.isArray(items) && items.length > 0) {
      items.forEach(item => {
        const option = document.createElement("option");
        option.value = item.name;
        option.textContent = item.title || item.name;
        dropdown.appendChild(option);
      });
    } else {
      dropdown.innerHTML = `<option>${emptyMessage}</option>`;
    }
  } catch (error) {
    console.error(`Error loading ${dropdownId}:`, error);
    dropdown.innerHTML = `<option>Error loading data</option>`;
    showMessage(`❌ Failed to load ${dropdownId}. Check console for details.`, "error");
  } finally {
    dropdown.disabled = false;
  }
}

/* ---------- Excel Upload ---------- */
async function handleExcelUpload() {
  const file = document.getElementById("excelFile").files[0];
  const nameDropdown = document.getElementById("selectfield");
  if (!file) return;

  nameDropdown.disabled = true;
  nameDropdown.innerHTML = `<option>Loading field names...</option>`;
  const formData = new FormData();
  formData.append("excel", file);

  try {
    const csrfToken = await getCsrfToken();
    const url = Granite.HTTP.externalize("/bin/getCFNames");

    const response = await fetch(url, {
      method: "POST",
      headers: { "CSRF-Token": csrfToken },
      body: formData,
      credentials: "same-origin"
    });

    if (!response.ok) throw new Error(`Failed to load column names (${response.status})`);

    const names = await response.json();
    console.log("Loaded column names:", names);
    nameDropdown.innerHTML = "";

    if (Array.isArray(names) && names.length > 0) {
      names.forEach(name => {
        const option = document.createElement("option");
        option.value = name;
        option.textContent = name;
        nameDropdown.appendChild(option);
      });
      showMessage("✅ Excel columns loaded successfully.", "success");
    } else {
      nameDropdown.innerHTML = `<option>No column names found</option>`;
      showMessage("⚠️ No column names found in the Excel file.", "warning");
    }
  } catch (err) {
    console.error("Error fetching CF names:", err);
    nameDropdown.innerHTML = `<option>Error loading names</option>`;
    showMessage("❌ Failed to load Excel column names. Check console for details.", "error");
  } finally {
    nameDropdown.disabled = false;
  }
}


/* ---------- Form Submit (Create CF) ---------- */
async function handleSubmit(event) {
  event.preventDefault();
  const form = document.getElementById("cfForm");

  if (!form.checkValidity()) {
    showMessage("⚠️ Please fill all required fields.", "warning");
    return;
  }

  showSpinner(); // 🌀 Show spinner before starting the request

  try {
    const formData = new FormData(form);
    const csrfToken = await getCsrfToken();
    const url = Granite.HTTP.externalize("/bin/content/createContentFragment");

    const response = await fetch(url, {
      method: "POST",
      headers: { "CSRF-Token": csrfToken },
      body: formData,
      credentials: "same-origin"
    });

    if (!response.ok) {
      const text = await response.text();
      console.error("Response text:", text);
      throw new Error(`HTTP ${response.status} - ${response.statusText}`);
    }

    const result = await response.json();
    console.log("Create CF result:", result);
    showMessage(result.status || "✅ Content Fragment created successfully!", "success");
  } catch (err) {
    console.error("Error submitting form:", err);
    showMessage("❌ Failed to create Content Fragment. Check console for details.", "error");
  } finally {
    hideSpinner(); // ✅ Hide spinner after success or failure
  }
}


/* ---------- Update CF ---------- */
async function handleUpdate() {
  const form = document.getElementById("cfForm");
  if (!form.checkValidity()) {
    showMessage("⚠️ Please fill all required fields before updating.", "warning");
    return;
  }

  // Step 1️⃣ — Ask confirmation *before* any request
  showConfirmation(
    "Are you sure you want to update or create Content Fragments based on this Excel?",
    async () => {
      // User confirmed — now perform the update
      showSpinner();

      const formData = new FormData(form);
      formData.append("mode", "update");

      try {
        const csrfToken = await getCsrfToken();
        const url = Granite.HTTP.externalize("/bin/updateCFs");

        const response = await fetch(url, {
          method: "POST",
          headers: { "CSRF-Token": csrfToken },
          body: formData,
          credentials: "same-origin"
        });

        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const result = await response.json();

        const created = Array.isArray(result.createFragments) ? result.createFragments : [];
        const updated = Array.isArray(result.updatedFragments) ? result.updatedFragments : [];
        const skipped = Array.isArray(result.skippedFragments) ? result.skippedFragments : [];

        let summaryHtml = `<strong>Update Summary</strong><br><br>`;
        summaryHtml += `<strong>Created content fragments:</strong><br>`;
        summaryHtml += created.length ? created.join("<br>") + "<br><br>" : "None<br><br>";

        summaryHtml += `<strong>Updated content fragments:</strong><br>`;
        summaryHtml += updated.length ? updated.join("<br>") + "<br><br>" : "None<br><br>";

        summaryHtml += `<strong>Skipped content fragments:</strong><br>`;
        summaryHtml += skipped.length ? skipped.join("<br>") + "<br>" : "None<br>";

        showMessage(summaryHtml, "success");

      } catch (err) {
        console.error("Error updating CFs:", err);
        showMessage("❌ Failed to update CFs. Check console for details.", "error");
      } finally {
        hideSpinner();
      }
    },
    () => {
      // User cancelled
      showMessage("Update cancelled by user.", "warning");
    }
  );
}



document.getElementById("updateBtn").addEventListener("click", handleUpdate);

/* ---------- CSRF Token ---------- */
async function getCsrfToken() {
  const res = await fetch(Granite.HTTP.externalize("/libs/granite/csrf/token.json"), {
    credentials: "same-origin"
  });
  const data = await res.json();
  return data.token;
}
/* ---------- Spinner Helpers ---------- */
function showSpinner() {
  document.getElementById("spinnerOverlay").style.display = "flex";
}

function hideSpinner() {
  document.getElementById("spinnerOverlay").style.display = "none";
}

</script>
