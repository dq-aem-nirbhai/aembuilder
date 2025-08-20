document.addEventListener("DOMContentLoaded", function () {
  const modeSelect = document.getElementById('creationMode');
  const extendsDiv = document.getElementById('extendsComponentDiv');

  function toggleSuperType() {
    if (modeSelect.value === 'extend') {
      extendsDiv.style.display = '';
    } else {
      extendsDiv.style.display = 'none';
      document.getElementById('superType').value = '';
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

  function createBaseRow(isNested, level = 0) {
    const template = document.getElementById('fieldRowTemplate');
    const div = template.cloneNode(true);
    div.removeAttribute('id');
    div.style.display = '';
    div.dataset.level = level;
    if (isNested) {
      div.classList.add('nested-row', 'mb-2');
      div.style.marginLeft = `${level * 20}px`;
      div.style.borderStyle = 'dashed';
      div.querySelector('.action-col').innerHTML = '<button type="button" class="btn btn-danger" onclick="removeNestedFieldRow(this)">-</button>';
    } else {
      div.classList.add('field-row', 'border', 'p-2', 'mb-3');
    }
    return div;
  }

  window.autoFillFieldName = function (labelInput) {
    const row = labelInput.closest('.field-row, .nested-row');
    const nameInput = row.querySelector('.fieldName');
    const labelValue = labelInput.value.trim();
    const camelCase = labelValue
      .replace(/[^a-zA-Z0-9 ]/g, '')
      .split(/\s+/)
      .map((w, i) => i === 0 ? w.toLowerCase() : w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
      .join('');
    nameInput.value = camelCase;
    validateFormFields();
  };

  window.handleFieldTypeChange = function (select) {
    const row = select.closest('.field-row, .nested-row');
    const type = select.value;
    const opt = row.querySelector('.options-container');
    const nested = row.querySelector('.nested-container');
    opt.innerHTML = '';
    nested.innerHTML = '';

    if (["select", "multiselect", "checkboxgroup", "radiogroup"].includes(type)) {
      const addBtn = document.createElement('button');
      addBtn.type = 'button';
      addBtn.className = 'btn btn-sm btn-secondary mb-2';
      addBtn.textContent = 'Add Option';
      addBtn.onclick = () => addOptionRow(addBtn);
      opt.appendChild(addBtn);
      addOptionRow(addBtn);
    } else if (type === 'multifield'||type==='tabs') {
      const addBtn = document.createElement('button');
      addBtn.type = 'button';
      addBtn.className = 'btn btn-sm btn-secondary mb-2';
      addBtn.textContent = 'Add Field';
      addBtn.onclick = () => addNestedFieldRow(addBtn);
      nested.appendChild(addBtn);
      addNestedFieldRow(addBtn);
    }
    updateIndexes();
  };

  window.addFieldRow = function () {
    const container = document.getElementById('fieldsContainer');
    const row = createBaseRow(false);
    row.querySelector('.action-col').innerHTML = '<button type="button" class="btn btn-danger" onclick="removeFieldRow(this)">-</button>';
    container.appendChild(row);
    updateIndexes();
    row.classList.add('animate__animated','animate__fadeIn');
    validateFormFields();
    updateMandatoryButtons();
  };

  window.removeFieldRow = function (btn) {
    btn.closest('.field-row').remove();
    updateIndexes();
    validateFormFields();
    updateMandatoryButtons();
  };

  function addNestedFieldRow(btn) {
    const container = btn.closest('.nested-container');
    const parent = container.closest('.field-row, .nested-row');
    const level = parseInt(parent.dataset.level || 0) + 1;
    const row = createBaseRow(true, level);
    container.insertBefore(row, btn);
    updateIndexes();
    validateFormFields();
  }
  window.removeNestedFieldRow = function (btn) {
    btn.closest('.nested-row').remove();
    updateIndexes();
    validateFormFields();
  };

  function addOptionRow(btn) {
    const container = btn.closest('.options-container');
    const div = document.createElement('div');
    div.className = 'option-row input-group mb-2';
    div.innerHTML = `<input type="text" class="form-control optionText" placeholder="Text" required>
      <input type="text" class="form-control optionValue" placeholder="Value" required>
      <button type="button" class="btn btn-danger" onclick="removeOptionRow(this)">-</button>`;
    container.insertBefore(div, btn);
    updateIndexes();
    validateFormFields();
  }
  window.removeOptionRow = function (btn) {
    btn.parentElement.remove();
    updateIndexes();
    validateFormFields();
  };

  function updateIndexes() {
    const fieldRows = document.querySelectorAll('#fieldsContainer > .field-row');
    fieldRows.forEach((row, i) => {
      setRowNames(row, `fields[${i}]`);
    });
    updateMandatoryButtons();
  }

  function setRowNames(row, prefix) {
    row.querySelector('.fieldLabel').name = `${prefix}.fieldLabel`;
    row.querySelector('.fieldName').name = `${prefix}.fieldName`;
    row.querySelector('.fieldType').name = `${prefix}.fieldType`;
    updateOptionIndexes(row, prefix);
    const nestedContainer = row.querySelector(':scope > .nested-container');
    if (nestedContainer) {
      const nestedRows = nestedContainer.querySelectorAll(':scope > .nested-row');
      nestedRows.forEach((nrow, idx) => {
        setRowNames(nrow, `${prefix}.nestedFields[${idx}]`);
      });
    }
  }

  function updateOptionIndexes(row, prefix) {
    const container = row.querySelector(':scope > .options-container');
    if (!container) return;
    const options = container.querySelectorAll(':scope > .option-row');
    options.forEach((opt, k) => {
      opt.querySelector('.optionText').name = `${prefix}.options[${k}].text`;
      opt.querySelector('.optionValue').name = `${prefix}.options[${k}].value`;
    });
  }

  function updateMandatoryButtons() {
    const rows = document.querySelectorAll('#fieldsContainer > .field-row');
    rows.forEach((row, idx) => {
      const actionCol = row.querySelector('.action-col');
      if (modeSelect.value === 'new' && idx === 0) {
        actionCol.innerHTML = '';
      } else {
        if (!actionCol.querySelector('button')) {
          actionCol.innerHTML = '<button type="button" class="btn btn-danger" onclick="removeFieldRow(this)">-</button>';
        }
      }
    });
  }

  const componentNameInput = document.getElementById('componentName');
  const errorDiv = document.getElementById('nameError');
  const createButton = document.getElementById('createButton');
  const projectName = document.getElementById('projectName').value;

  function debounce(func, delay) {
    let timer;
    return function (...args) {
      clearTimeout(timer);
      timer = setTimeout(() => func.apply(this, args), delay);
    };
  }

  const checkComponentNameAvailability = debounce(() => {
    const componentName = componentNameInput.value.trim();
    if (!componentName) {
      errorDiv.innerText = '';
      errorDiv.classList.remove('text-danger', 'text-success');
      componentNameInput.classList.remove('is-invalid');
      validateFormFields();
      return;
    }

    fetch(`/check-componentName/${projectName}?componentName=${encodeURIComponent(componentName)}`)
      .then(response => response.json())
      .then(isAvailable => {
        if (isAvailable === false) {
          errorDiv.innerText = '⚠️ Component name already exists. Please choose another.';
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
        errorDiv.innerText = '⚠️ Server error while checking component name.';
        errorDiv.classList.add('text-danger');
        errorDiv.classList.remove('text-success');
        componentNameInput.classList.add('is-invalid');
        createButton.disabled = true;
      });
  }, 400);

  componentNameInput.addEventListener('input', checkComponentNameAvailability);

  window.validateFormFields = function () {
    const name = componentNameInput.value.trim();
    const group = document.getElementById('componentGroup').value;
    const mode = modeSelect.value;
    const superTypeValue = document.getElementById('superType').value;
    if (!name || !group || componentNameInput.classList.contains('is-invalid')) {
      createButton.disabled = true;
      return;
    }
    if (mode === 'extend' && !superTypeValue) {
      createButton.disabled = true;
      return;
    }
    const rows = document.querySelectorAll('#fieldsContainer .field-row, #fieldsContainer .nested-row');
    if (mode === 'new' && rows.length === 0) {
      createButton.disabled = true;
      return;
    }
    for (let row of rows) {
      const label = row.querySelector('.fieldLabel').value.trim();
      const fname = row.querySelector('.fieldName').value.trim();
      const type = row.querySelector('.fieldType').value;
      if (!label || !fname || !type) {
        createButton.disabled = true;
        return;
      }
      const optionInputs = row.querySelectorAll('.option-row input');
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

  if (window.editMode) {
    loadComponentData(window.componentData || {});
  }

  function loadComponentData(data) {
    if (!data) return;
    componentNameInput.value = data.componentName || '';
    componentNameInput.readOnly = true;
    document.getElementById('componentGroup').value = data.componentGroup || '';
    if (data.superType) {
      modeSelect.value = 'extend';
      toggleSuperType();
      document.getElementById('superType').value = data.superType;
    } else {
      modeSelect.value = 'new';
      toggleSuperType();
    }
    const container = document.getElementById('fieldsContainer');
    container.innerHTML = '';
    if (data.fields) {
      data.fields.forEach(f => {
        const row = createBaseRow(false);
        row.querySelector('.action-col').innerHTML = '<button type="button" class="btn btn-danger" onclick="removeFieldRow(this)">-</button>';
        container.appendChild(row);
        populateFieldRow(row, f);
      });
    }
    updateIndexes();
    validateFormFields();
  }

function populateFieldRow(row, field, level = 0) {
  // Fill label, name, and type fields
  row.querySelector('.fieldLabel').value = field.fieldLabel || '';
  row.querySelector('.fieldName').value = field.fieldName || '';
  row.querySelector('.fieldType').value = field.fieldType || '';

  // Trigger UI changes based on field type (options or nested fields)
  handleFieldTypeChange(row.querySelector('.fieldType'));

  // === Handle Options ===
  if (field.options && field.options.length > 0) {
    const container = row.querySelector('.options-container');

    // Save the Add Option button before clearing
    const addBtn = container.querySelector('button');
    container.innerHTML = '';

    field.options.forEach(opt => {
      const div = document.createElement('div');
      div.className = 'option-row input-group mb-2';
      div.innerHTML = `
        <input type="text" class="form-control optionText" placeholder="Text" value="${opt.text || ''}" required>
        <input type="text" class="form-control optionValue" placeholder="Value" value="${opt.value || ''}" required>
        <button type="button" class="btn btn-danger" onclick="removeOptionRow(this)">-</button>`;
      container.appendChild(div);
    });

    if (addBtn) {
      container.appendChild(addBtn);
    }
  }

  // === Handle Nested Fields ===
if ((field.fieldType === 'multifield' || field.fieldType === 'tabs')
    && field.nestedFields && Array.isArray(field.nestedFields)) {
  const container = row.querySelector('.nested-container');

  // Save Add Field button before clearing
  const addBtn = container.querySelector('button');
  container.innerHTML = '';

  field.nestedFields.forEach(nestedField => {
    const nestedRow = createBaseRow(true, level + 1);   // 🔹 stays same, reuses nested-row logic
    container.appendChild(nestedRow);
    populateFieldRow(nestedRow, nestedField, level + 1);
  });

  if (addBtn) {
    container.appendChild(addBtn);
  } else {
    const newAddBtn = document.createElement('button');
    newAddBtn.type = 'button';
    newAddBtn.className = 'btn btn-sm btn-secondary mb-2';
    newAddBtn.textContent = 'Add Field';
    newAddBtn.onclick = () => addNestedFieldRow(newAddBtn);
    container.appendChild(newAddBtn);
  }
}

  // Mark form valid again (optional)
  validateFormFields();
}

});
