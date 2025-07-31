document.addEventListener("DOMContentLoaded", function () {
  const typeOptions = document.querySelector('.fieldType').innerHTML;
  const projectName = document.getElementById('projectName').value;
  const componentType = document.getElementById('componentType');
  const extendsDiv = document.getElementById('extendsDiv');
  const extendsSelect = document.getElementById('extendsComponent');

  document.querySelectorAll('.fieldName').forEach(f => {
    f.dataset.touched = 'false';
  });

  function loadAvailableComponents() {
    fetch(`/available-components/${projectName}`)
      .then(res => res.json())
      .then(data => {
        extendsSelect.innerHTML = '<option value="">-- Select Component --</option>';
        data.forEach(item => {
          const opt = document.createElement('option');
          opt.value = item;
          opt.textContent = item.split('/').pop();
          extendsSelect.appendChild(opt);
        });
      });
  }

  function loadInheritedFields(path) {
    if (!path) return;
    fetch(`/component/fields?path=${encodeURIComponent(path)}`)
      .then(res => res.json())
      .then(data => {
        populateFields(data);
      });
  }

  function handleTypeChange() {
    if (componentType.value === 'extend') {
      extendsDiv.style.display = 'block';
      loadAvailableComponents();
    } else {
      extendsDiv.style.display = 'none';
      extendsSelect.innerHTML = '';
    }
    validateFormFields();
  }

  componentType.addEventListener('change', handleTypeChange);
  if (extendsSelect) {
    extendsSelect.addEventListener('change', () => {
      loadInheritedFields(extendsSelect.value);
      validateFormFields();
    });
  }
  handleTypeChange();

  function createBaseRow(isNested, level = 0) {
    const div = document.createElement('div');
    div.className = isNested ? 'nested-row mb-2' : 'field-row border p-2 mb-3';
    div.dataset.level = level;
    if (isNested) {
      div.style.marginLeft = `${level * 20}px`;
      div.style.borderStyle = 'dashed';
    }
    div.innerHTML = `
      <div class="row g-2 align-items-end">
        <div class="col">
          <input type="text" class="form-control fieldLabel" placeholder="Field Label" oninput="autoFillFieldName(this)" required>
        </div>
        <div class="col">
          <input type="text" class="form-control fieldName" placeholder="Field Name" oninput="markFieldNameTouched(this)" required>
        </div>
        <div class="col">
          <select class="form-select fieldType" onchange="handleFieldTypeChange(this)" required>${typeOptions}</select>
        </div>
        <div class="col-auto action-col">
          ${isNested ? '<button type="button" class="btn btn-danger" onclick="removeNestedFieldRow(this)">-</button>' : ''}
        </div>
      </div>
      <div class="options-container mt-2"></div>
      <div class="nested-container mt-2"></div>`;
    div.querySelector('.fieldName').dataset.touched = 'false';
    return div;
  }

  window.autoFillFieldName = function (labelInput) {
    const row = labelInput.closest('.field-row, .nested-row');
    const nameInput = row.querySelector('.fieldName');
    if (nameInput.dataset.touched === 'true') {
      validateFormFields();
      return;
    }
    const labelValue = labelInput.value.trim();
    const camelCase = labelValue
      .replace(/[^a-zA-Z0-9 ]/g, '')
      .split(/\s+/)
      .map((w, i) => i === 0 ? w.toLowerCase() : w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
      .join('');
    nameInput.value = camelCase;
    nameInput.dataset.autofilled = 'true';
    validateFormFields();
  };

  window.markFieldNameTouched = function (input) {
    input.dataset.touched = 'true';
    input.dataset.autofilled = 'false';
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
    } else if (type === 'multifield') {
      const addBtn = document.createElement('button');
      addBtn.type = 'button';
      addBtn.className = 'btn btn-sm btn-secondary mb-2';
      addBtn.textContent = 'Add Field';
      addBtn.onclick = () => addNestedFieldRow(addBtn);
      nested.appendChild(addBtn);
      addNestedFieldRow(addBtn);
    }
    updateIndexes();
    validateFormFields();
  };

  window.addFieldRow = function (data) {
    const container = document.getElementById('fieldsContainer');
    const row = createBaseRow(false);
    row.querySelector('.action-col').innerHTML = '<button type="button" class="btn btn-danger" onclick="removeFieldRow(this)">-</button>';
    container.appendChild(row);
    if (data) fillRowFromData(row, data);
    updateIndexes();
    row.classList.add('animate__animated','animate__fadeIn');
    validateFormFields();
  };

  window.removeFieldRow = function (btn) {
    btn.closest('.field-row').remove();
    updateIndexes();
    validateFormFields();
  };

  function addNestedFieldRow(btn, data) {
    const container = btn.closest('.nested-container');
    const parent = container.closest('.field-row, .nested-row');
    const level = parseInt(parent.dataset.level || 0) + 1;
    const row = createBaseRow(true, level);
    container.insertBefore(row, btn);
    if (data) fillRowFromData(row, data);
    updateIndexes();
    validateFormFields();
  }
  window.removeNestedFieldRow = function (btn) {
    btn.closest('.nested-row').remove();
    updateIndexes();
    validateFormFields();
  };

  function addOptionRow(btn, option) {
    const container = btn.closest('.options-container');
    const div = document.createElement('div');
    div.className = 'option-row input-group mb-2';
    div.innerHTML = `<input type="text" class="form-control optionText" placeholder="Text" required>
      <input type="text" class="form-control optionValue" placeholder="Value" required>
      <button type="button" class="btn btn-danger" onclick="removeOptionRow(this)">-</button>`;
    if (option) {
      div.querySelector('.optionText').value = option.text || '';
      div.querySelector('.optionValue').value = option.value || '';
    }
    container.insertBefore(div, btn);
    updateIndexes();
    validateFormFields();
  }

  function fillRowFromData(row, data) {
    row.querySelector('.fieldLabel').value = data.fieldLabel || '';
    row.querySelector('.fieldName').value = data.fieldName || '';
    row.querySelector('.fieldName').dataset.touched = 'true';
    const typeSelect = row.querySelector('.fieldType');
    typeSelect.value = data.fieldType || '';
    handleFieldTypeChange(typeSelect);
    if (data.options && data.options.length) {
      const addBtn = row.querySelector('.options-container button');
      data.options.forEach(opt => addOptionRow(addBtn, opt));
    }
    if (data.nestedFields && data.nestedFields.length) {
      const container = row.querySelector('.nested-container');
      const addBtn = container.querySelector('button');
      data.nestedFields.forEach(nf => addNestedFieldRow(addBtn, nf));
    }
  }

  function populateFields(fields) {
    const container = document.getElementById('fieldsContainer');
    container.innerHTML = '';
    fields.forEach(f => {
      const row = createBaseRow(false);
      row.querySelector('.action-col').innerHTML = '<button type="button" class="btn btn-danger" onclick="removeFieldRow(this)">-</button>';
      container.appendChild(row);
      fillRowFromData(row, f);
    });
    if (fields.length === 0) {
      window.addFieldRow();
    }
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

  const componentNameInput = document.getElementById('componentName');
  const errorDiv = document.getElementById('nameError');
  const createButton = document.getElementById('createButton');

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
    if (!name || !group || componentNameInput.classList.contains('is-invalid')) {
      createButton.disabled = true;
      return;
    }
    if (componentType.value === 'extend' && !extendsSelect.value) {
      createButton.disabled = true;
      return;
    }
    const rows = document.querySelectorAll('.field-row, .nested-row');
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
});
