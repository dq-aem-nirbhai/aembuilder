document.addEventListener("DOMContentLoaded", function () {
  const modeSelect = document.getElementById('creationMode');
  const extendsDiv = document.getElementById('extendsComponentDiv');
  const componentNameInput = document.getElementById('componentName');
  const errorDiv = document.getElementById('nameError');
  const createButton = document.getElementById('createButton');
  const projectName = document.getElementById('projectName') ? document.getElementById('projectName').value : '';

  // ===== Toggle superType for extend/new component =====
  function toggleSuperType() {
    if (modeSelect.value === 'extend') {
      extendsDiv.style.display = '';
    } else {
      extendsDiv.style.display = 'none';
      const st = document.getElementById('superType');
      if (st) st.value = '';
      if (document.getElementById('fieldsContainer').childElementCount === 0) {
        addFieldRow();
      }
    }
    updateMandatoryButtons();
  }

  modeSelect.addEventListener('change', () => {
    toggleSuperType();
    validateFormFields();
  });
  toggleSuperType();

  // ===== Base Row Creation =====
  function createBaseRow(isNested, level = 0) {
    const template = document.getElementById('fieldRowTemplate');
    const div = template.cloneNode(true);
    div.removeAttribute('id');
    div.style.display = '';
    div.dataset.level = level;

    // ensure class for top-level vs nested
    if (isNested) {
      div.classList.add('nested-row', 'mb-2');
      div.style.marginLeft = `${level * 20}px`;
      div.style.borderStyle = 'dashed';
      // action button for nested removal (keeps same function name)
      div.querySelector('.action-col').innerHTML =
        '<button type="button" class="btn btn-danger" onclick="removeNestedFieldRow(this)">-</button>';
    } else {
      div.classList.add('field-row', 'border', 'p-2', 'mb-3');
    }

    // Bind local event handlers so dynamically created rows work
    const labelEl = div.querySelector('.fieldLabel');
    if (labelEl) labelEl.addEventListener('input', function () { autoFillFieldName(this); });

    const typeEl = div.querySelector('.fieldType');
    if (typeEl) {
      // keep inline onchange as well (template has onchange), but also bind listener
      typeEl.addEventListener('change', function () {
        // only run the "creation-mode" UI logic here (do not wipe data during populate)
        handleFieldTypeChange(this);
        updateIndexes();
        validateFormFields();
      });
    }

    return div;
  }

  // expose to global for inline handler compatibility
  window.createBaseRow = createBaseRow;

  // ===== Auto-fill camelCase fieldName =====
  window.autoFillFieldName = function (labelInput) {
    const row = labelInput.closest('.field-row, .nested-row');
    let labelValue = labelInput.value.trim();
    if (labelValue.length > 0) {
      labelValue =
        labelValue.charAt(0).toUpperCase() + labelValue.slice(1).toLowerCase();
      labelInput.value = labelValue;
    }
    const nameInput = row.querySelector('.fieldName');
    const camelCase = labelValue
      .replace(/[^a-zA-Z0-9 ]/g, '')
      .split(/\s+/)
      .map((w, i) =>
        i === 0
          ? w.toLowerCase()
          : w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()
      )
      .join('');
    if (nameInput) nameInput.value = camelCase;
    validateFormFields();
  };

  // ===== Field Type Handling (creation-mode UI builder) =====
  // Keeps original behavior: adds Add Option/Add Field buttons and one child row.
  window.handleFieldTypeChange = function (select) {
    if (!select) return;
    const row = select.closest('.field-row, .nested-row');
    const type = select.value;
    const opt = row.querySelector('.options-container');
    const nested = row.querySelector('.nested-container');

    // clear both containers
    if (opt) opt.innerHTML = '';
    if (nested) nested.innerHTML = '';

    if (["select", "multiselect", "checkboxgroup", "radiogroup"].includes(type)) {
      const addBtn = document.createElement('button');
      addBtn.type = 'button';
      addBtn.className = 'btn btn-sm btn-secondary mb-2';
      addBtn.textContent = 'Add Option';
      addBtn.addEventListener('click', () => addTextValueRow(addBtn));
      opt.appendChild(addBtn);
      addTextValueRow(addBtn); // create initial option row
    } else if (type === 'multifield' || type === 'tabs') {
      const addBtn = document.createElement('button');
      addBtn.type = 'button';
      addBtn.className = 'btn btn-sm btn-secondary mb-2';
      addBtn.textContent = 'Add Field';
      addBtn.addEventListener('click', () => addNestedFieldRow(addBtn));
      nested.appendChild(addBtn);
      addNestedFieldRow(addBtn); // create initial nested field
    }
    updateIndexes();
  };

  // ===== Add/Remove Main Field =====
  window.addFieldRow = function () {
    const container = document.getElementById('fieldsContainer');
    const row = createBaseRow(false);
    // top-level remove button (same name as before)
    row.querySelector('.action-col').innerHTML =
      '<button type="button" class="btn btn-danger" onclick="removeFieldRow(this)">-</button>';
    container.appendChild(row);
    updateIndexes();
    row.classList.add('animate__animated', 'animate__fadeIn');
    validateFormFields();
    updateMandatoryButtons();
  };

  window.removeFieldRow = function (btn) {
    btn.closest('.field-row').remove();
    updateIndexes();
    validateFormFields();
    updateMandatoryButtons();
  };

  // ===== Add/Remove Nested Field =====
  function addNestedFieldRow(btn) {
    const container = btn.closest('.nested-container');
    const parent = container.closest('.field-row, .nested-row');
    const level = parseInt(parent.dataset.level || 0) + 1;
    const row = createBaseRow(true, level);
    // insert before the add button
    container.insertBefore(row, btn);
    updateIndexes();
    validateFormFields();
  }

  window.removeNestedFieldRow = function (btn) {
    btn.closest('.nested-row').remove();
    updateIndexes();
    validateFormFields();
  };

  // ===== Add Text/Value row for select/multiselect/radio/checkboxgroup =====
  function addTextValueRow(btn, text = '', value = '') {
    // find options-container (closest from the button)
    const container = btn.closest('.options-container');
    // create row
    const div = document.createElement('div');
    div.className = 'option-row nested-row input-group mb-2';
    div.innerHTML = `
      <input type="text" class="form-control optionText" placeholder="Text" value="${escapeHtml(text)}" required>
      <input type="text" class="form-control optionValue" placeholder="Value" value="${escapeHtml(value)}" required>
      <button type="button" class="btn btn-danger" onclick="removeOptionRow(this)">-</button>
    `;
    // insert before add button (so add button remains at end)
    container.insertBefore(div, btn);
    updateIndexes();
    validateFormFields();
  }

  // expose removeOptionRow (called from inline onclick and also used above)
  window.removeOptionRow = function (btn) {
    const parent = btn.parentElement;
    if (parent) parent.remove();
    updateIndexes();
    validateFormFields();
  };

  // small helper to avoid raw html injection
  function escapeHtml(str) {
    if (str === undefined || str === null) return '';
    return String(str).replace(/&/g, '&amp;').replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  // ===== Index Management =====
  function updateIndexes() {
    const fieldRows = document.querySelectorAll('#fieldsContainer > .field-row');
    fieldRows.forEach((row, i) => {
      setRowNames(row, `fields[${i}]`);
    });
    updateMandatoryButtons();
  }

  function setRowNames(row, prefix) {
    const labelEl = row.querySelector('.fieldLabel');
    const nameEl = row.querySelector('.fieldName');
    const typeEl = row.querySelector('.fieldType');

    if (labelEl) labelEl.name = `${prefix}.fieldLabel`;
    if (nameEl) nameEl.name = `${prefix}.fieldName`;
    if (typeEl) typeEl.name = `${prefix}.fieldType`;

    // options-container: direct option rows (option-row)
    const optionsContainer = row.querySelector(':scope > .options-container');
    if (optionsContainer) {
      const optionRows = optionsContainer.querySelectorAll(':scope > .option-row');
      optionRows.forEach((optRow, idx) => {
        const t = optRow.querySelector('.optionText');
        const v = optRow.querySelector('.optionValue');
        if (t) t.name = `${prefix}.options[${idx}].text`;
        if (v) v.name = `${prefix}.options[${idx}].value`;
      });
    }

    // nested fields: direct field-row children in nested-container
    const nestedContainer = row.querySelector(':scope > .nested-container');
    if (nestedContainer) {
      const nestedRows = nestedContainer.querySelectorAll(':scope > .nested-row');
      // nested-row can be either an option-row or a nested field-row (we distinguish by presence of .fieldLabel)
      let nestedFieldIndex = 0;
      nestedRows.forEach((nrow) => {
        const nestedLabel = nrow.querySelector('.fieldLabel');
        if (nestedLabel) {
          // this is a nested FIELD (created by createBaseRow)
          setRowNames(nrow, `${prefix}.nestedFields[${nestedFieldIndex}]`);
          nestedFieldIndex++;
        }
        // if it's an option-row, above options handling already assigned names when it's inside options-container
      });
    }
  }

  function updateMandatoryButtons() {
    const rows = document.querySelectorAll('#fieldsContainer > .field-row');
    rows.forEach((row, idx) => {
      const actionCol = row.querySelector('.action-col');
      if (!actionCol) return;
      if (modeSelect.value === 'new' && idx === 0) {
        actionCol.innerHTML = '';
      } else {
        if (!actionCol.querySelector('button')) {
          actionCol.innerHTML =
            '<button type="button" class="btn btn-danger" onclick="removeFieldRow(this)">-</button>';
        }
      }
    });
  }

  // ===== Component Name Validation (unchanged) =====
  componentNameInput.addEventListener("input", function () {
    // Only letters, numbers, underscore
    const cleaned = componentNameInput.value.replace(/[^A-Za-z0-9_]/g, "");
    if (componentNameInput.value !== cleaned) {
      componentNameInput.value = cleaned;
    }
    checkComponentNameAvailability();
  });

  function debounce(func, delay) {
    let timer;
    return function (...args) {
      clearTimeout(timer);
      timer = setTimeout(() => func.apply(this, args), delay);
    };
  }

  // keep the same global name check function signature as your code expects
  const checkComponentNameAvailability = debounce(() => {
    const componentName = componentNameInput.value.trim();
    if (!componentName) {
      errorDiv.innerText = '';
      errorDiv.classList.remove('text-danger', 'text-success');
      componentNameInput.classList.remove('is-invalid');
      validateFormFields();
      return;
    }
    fetch(
      `/check-componentName/${projectName}?componentName=${encodeURIComponent(componentName)}`
    )
      .then((response) => response.json())
      .then((isAvailable) => {
        if (isAvailable === false) {
          errorDiv.innerText =
            '⚠️ Component name already exists. Please choose another.';
          errorDiv.classList.add('text-danger');
          errorDiv.classList.remove('text-success');
          componentNameInput.classList.add('is-invalid');
          createButton.disabled = true;
        } else {
          errorDiv.innerText = '✅ Component name is available.';
          errorDiv.classList.remove('text-danger');
          errorDiv.classList.add('text-success');
          componentNameInput.classList.remove('is-invalid');
          validateFormFields();
        }
      })
      .catch(() => {
        errorDiv.innerText =
          '⚠️ Server error while checking component name.';
        errorDiv.classList.add('text-danger');
        errorDiv.classList.remove('text-success');
        componentNameInput.classList.add('is-invalid');
        createButton.disabled = true;
      });
  }, 400);

  // ===== Form Validation (keeps original behavior) =====
  window.validateFormFields = function () {
    const name = componentNameInput.value.trim();
    const groupEl = document.getElementById('componentGroup');
    const group = groupEl ? groupEl.value : '';
    const mode = modeSelect.value;
    const superTypeEl = document.getElementById('superType');
    const superTypeValue = superTypeEl ? superTypeEl.value : '';

    if (!name || !group || componentNameInput.classList.contains('is-invalid')) {
      createButton.disabled = true;
      return;
    }
    if (mode === 'extend' && !superTypeValue) {
      createButton.disabled = true;
      return;
    }
    const rows = document.querySelectorAll(
      '#fieldsContainer .field-row, #fieldsContainer .nested-row'
    );
    if (mode === 'new' && rows.length === 0) {
      createButton.disabled = true;
      return;
    }
    for (let row of rows) {
      const label = row.querySelector('.fieldLabel');
      const fname = row.querySelector('.fieldName');
      const type = row.querySelector('.fieldType');
      if (label && !label.value.trim()) {
        createButton.disabled = true;
        return;
      }
      if (fname && !fname.value.trim()) {
        createButton.disabled = true;
        return;
      }
      if (type && !type.value) {
        createButton.disabled = true;
        return;
      }
      const optionInputs = row.querySelectorAll('.optionText, .optionValue');
      for (let inp of optionInputs) {
        if (!inp.value.trim()) {
          createButton.disabled = true;
          return;
        }
      }
    }
    createButton.disabled = false;
  };

  document.addEventListener('input', validateFormFields);
  document.addEventListener('change', validateFormFields);
  updateIndexes();

  // ===== Load Component Data (Edit Mode) =====
  if (window.editMode) {
    loadComponentData(window.componentData || {});
  }

  function loadComponentData(data) {
    if (!data) return;
    if (data.componentName) {
      componentNameInput.value = data.componentName;
      componentNameInput.readOnly = true;
    }
    const groupSelect = document.getElementById('componentGroup');
    if (groupSelect && data.componentGroup) groupSelect.value = data.componentGroup;

    if (data.superType) {
      modeSelect.value = 'extend';
      toggleSuperType();
      const st = document.getElementById('superType');
      if (st) st.value = data.superType;
    } else {
      modeSelect.value = 'new';
      toggleSuperType();
    }

    const container = document.getElementById('fieldsContainer');
    container.innerHTML = '';

    if (Array.isArray(data.fields)) {
      data.fields.forEach((f) => {
        const row = createBaseRow(false);
        row.querySelector('.action-col').innerHTML =
          '<button type="button" class="btn btn-danger" onclick="removeFieldRow(this)">-</button>';
        container.appendChild(row);
        populateFieldRow(row, f);
      });
    }
    updateIndexes();
    validateFormFields();
  }

  // IMPORTANT: populateFieldRow now builds the UI **directly** from saved data
  // (instead of calling handleFieldTypeChange and then losing values).
  function populateFieldRow(row, field, level = 0) {
    if (!row || !field) return;

    const labelEl = row.querySelector('.fieldLabel');
    const nameEl = row.querySelector('.fieldName');
    const typeEl = row.querySelector('.fieldType');

    if (labelEl) labelEl.value = field.fieldLabel || '';
    if (nameEl) nameEl.value = field.fieldName || '';
    if (typeEl) typeEl.value = field.fieldType || '';

    // Clear containers - we'll populate explicitly
    const optionsContainer = row.querySelector('.options-container');
    const nestedContainer = row.querySelector('.nested-container');
    if (optionsContainer) optionsContainer.innerHTML = '';
    if (nestedContainer) nestedContainer.innerHTML = '';

    // Restore options for select/multiselect/checkboxgroup/radiogroup
    if (
      ["select", "multiselect", "checkboxgroup", "radiogroup"].includes(field.fieldType) &&
      Array.isArray(field.options)
    ) {
      // add each saved option
      field.options.forEach((opt) => {
        const div = document.createElement('div');
        div.className = 'option-row nested-row input-group mb-2';
        div.innerHTML = `
          <input type="text" class="form-control optionText" placeholder="Text" value="${escapeHtml(opt.text || '')}" required>
          <input type="text" class="form-control optionValue" placeholder="Value" value="${escapeHtml(opt.value || '')}" required>
          <button type="button" class="btn btn-danger" onclick="removeOptionRow(this)">-</button>`;
        optionsContainer.appendChild(div);
        // attach remove listener (also supports inline onclick above)
        const btn = div.querySelector('button');
        if (btn) btn.addEventListener('click', () => { div.remove(); updateIndexes(); validateFormFields(); });
      });
      // add "Add Option" button
      const newAddBtn = document.createElement('button');
      newAddBtn.type = 'button';
      newAddBtn.className = 'btn btn-sm btn-secondary mb-2';
      newAddBtn.textContent = 'Add Option';
      newAddBtn.addEventListener('click', () => addTextValueRow(newAddBtn));
      optionsContainer.appendChild(newAddBtn);
    }

    // Restore nestedFields for multifield/tabs
    if (
      (field.fieldType === 'multifield' || field.fieldType === 'tabs') &&
      Array.isArray(field.nestedFields)
    ) {
      field.nestedFields.forEach((nestedField) => {
        const nestedRow = createBaseRow(true, level + 1);
        nestedContainer.appendChild(nestedRow);
        populateFieldRow(nestedRow, nestedField, level + 1);
      });
      const newAddBtn = document.createElement('button');
      newAddBtn.type = 'button';
      newAddBtn.className = 'btn btn-sm btn-secondary mb-2';
      newAddBtn.textContent = 'Add Field';
      newAddBtn.addEventListener('click', () => addNestedFieldRow(newAddBtn));
      nestedContainer.appendChild(newAddBtn);
    }

    // After populating, update names & validation
    updateIndexes();
    validateFormFields();
  }
});
