document.addEventListener("DOMContentLoaded", function () {
    const modeSelect = document.getElementById('creationMode');
    const extendsDiv = document.getElementById('extendsComponentDiv');
    const componentNameInput = document.getElementById('componentName');
    const errorDiv = document.getElementById('nameError');
    const createButton = document.getElementById('createButton');
    const projectName = document.getElementById('projectName') ? document.getElementById('projectName').value : '';
    const superTypeSelect = document.getElementById('superType');
    const fieldsContainer = document.getElementById('fieldsContainer');
    const fieldRowTemplate = document.getElementById('fieldRowTemplate');
    const parentTabsContainer = document.getElementById('parentTabsContainer');

    // ===== Debounce =====
    function debounce(func, delay) {
        let timer;
        return function (...args) {
            clearTimeout(timer);
            timer = setTimeout(() => func.apply(this, args), delay);
        };
    }

    // ===== Forbidden words for component name (case-insensitive) =====
    const forbiddenWords = ["new", "hello"]; // add more words as needed

    // ===== Helper: is this field input inside a multifield row? =====
    function isFieldTypeMultifield(fieldInput) {
        const row = fieldInput.closest('.field-row, .nested-row');
        return row?.querySelector('.fieldType')?.value === 'multifield';
    }

    // ===== Toggle superType for extend/new component =====
    function toggleSuperType() {
        if (modeSelect.value === 'extend') {
            extendsDiv.style.display = '';
            // if user already selected a superType, trigger fetch to populate
            if (superTypeSelect && superTypeSelect.value) {
                fetchParentTabsAndFields(superTypeSelect.value);
            }
        } else {
            extendsDiv.style.display = 'none';
            const st = document.getElementById('superType');
            if (st) st.value = '';
            if (fieldsContainer && fieldsContainer.childElementCount === 0) {
                addFieldRow();
            }
            if (parentTabsContainer) parentTabsContainer.innerHTML = '';
        }
        updateMandatoryButtons();
    }

    modeSelect.addEventListener('change', () => {
        toggleSuperType();
        validateFormFields();
    });
    toggleSuperType();

    // ===== Create Base Row =====
    function createBaseRow(isNested = false, level = 0) {
        if (!fieldRowTemplate) {
            console.error('fieldRowTemplate not found in DOM.');
            const div = document.createElement('div');
            return div;
        }
        // If template is a <template> element (recommended), use .content.cloneNode(true)
        let div;
        if (fieldRowTemplate.tagName && fieldRowTemplate.tagName.toLowerCase() === 'template') {
            const clone = fieldRowTemplate.content.cloneNode(true);
            // wrap in a container to return a single element
            const wrapper = document.createElement('div');
            wrapper.appendChild(clone);
            // We expect inside template a single .field-row; find it
            div = wrapper.querySelector('.field-row, .nested-row');
            if (!div) {
                // fallback: create a simple row structure
                div = document.createElement('div');
                div.className = isNested ? 'nested-row' : 'field-row';
            } else {
                // clone it to detach from wrapper
                div = div.cloneNode(true);
            }
        } else {
            // template element is a DOM element to clone
            const cloned = fieldRowTemplate.cloneNode(true);
            div = cloned;
        }

        div.removeAttribute('id');
        div.style.display = '';
        div.dataset.level = level;

        if (isNested) {
            div.classList.add('nested-row', 'mb-2');
            div.style.marginLeft = `${level * 20}px`;
            div.style.borderStyle = 'dashed';
            const actionCol = div.querySelector('.action-col');
            if (actionCol) actionCol.innerHTML = '<button type="button" class="btn btn-danger" onclick="removeNestedFieldRow(this)">-</button>';
        } else {
            div.classList.add('field-row', 'border', 'p-2', 'mb-3');
            const actionCol = div.querySelector('.action-col');
            if (actionCol && !actionCol.querySelector('button')) {
                actionCol.innerHTML = '<button type="button" class="btn btn-danger" onclick="removeFieldRow(this)">-</button>';
            }
        }

        const labelEl = div.querySelector('.fieldLabel');
        if (labelEl) {
            labelEl.addEventListener('input', function () {
                autoFillFieldName(this);
            });
        }

        const typeEl = div.querySelector('.fieldType');
        if (typeEl) {
            typeEl.addEventListener('change', function () {
                handleFieldTypeChange(this);
                updateIndexes();

                // When the type changes, run multifield-specific validation only if new type is multifield
                const row = this.closest('.field-row, .nested-row');
                const nameInput = row.querySelector('.fieldName');
                if (nameInput) {
                    if (this.value === 'multifield') {
                        // run duplicate check and server check for multifield
                        checkDuplicateFieldNameWithinComponent(nameInput);
                        checkFieldNameAvailability(nameInput);
                    } else {
                        // Clear any multifield-only errors when switching away
                        const err = nameInput.nextElementSibling;
                        if (err) err.innerText = '';
                        nameInput.classList.remove('is-invalid');
                        nameInput.classList.remove('is-valid');
                    }
                }

                validateFormFields();
            });
        }

        return div;
    }

    // Expose globally for template buttons that call createBaseRow() from inline onclicks
    window.createBaseRow = createBaseRow;

    // ===== Auto-fill camelCase fieldName + conditional checks =====
    window.autoFillFieldName = function (labelInput) {
        const row = labelInput.closest('.field-row, .nested-row');
        let labelValue = labelInput.value.trim();

        if (labelValue.length > 0) {
            labelValue = labelValue.charAt(0).toUpperCase() + labelValue.slice(1).toLowerCase();
            labelInput.value = labelValue;
        }

        const nameInput = row.querySelector('.fieldName');
        const camelCase = labelValue
            .replace(/[^a-zA-Z0-9 ]/g, '')
            .split(/\s+/)
            .map((w, i) =>
                i === 0 ? w.toLowerCase() : w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()
            )
            .join('');

        if (nameInput) {
            nameInput.value = camelCase;
            // only run server & duplicate checks for multifield (local duplicate function already guards)
            checkDuplicateFieldNameWithinComponent(nameInput);
            if (isFieldTypeMultifield(nameInput)) {
                checkFieldNameAvailability(nameInput);
            }
        }
        validateFormFields();
    };

    // ===== Check duplicate field names in same component (multifield only) =====
    function checkDuplicateFieldNameWithinComponent(fieldInput) {
  const fieldName = fieldInput.value.trim();
  if (!fieldName) return false;

  const allRows = Array.from(document.querySelectorAll('#fieldsContainer .field-row, #fieldsContainer .nested-row'));
  const duplicates = allRows.filter(row => {
    const fname = row.querySelector('.fieldName')?.value.trim();
    return fname && fname === fieldName;
  });

  const errorDiv = fieldInput.nextElementSibling || createFieldErrorDiv(fieldInput);

  const fieldTypeSelect = fieldInput.closest('.field-row')?.querySelector('.fieldType');

  if (duplicates.length > 1) {
    // Show error message
    errorDiv.innerText = '⚠️ Duplicate name detected!';
    errorDiv.classList.add('text-danger');
    fieldInput.classList.add('is-invalid');

    // Disable field type selection
    if (fieldTypeSelect) fieldTypeSelect.disabled = true;

    // Disable Create button
    document.getElementById('createButton').disabled = true;
    return true;
  } else {
    // Clear message
    const srvErr = errorDiv.dataset.serverError === 'true';
    if (!srvErr) {
      errorDiv.innerText = '';
    }
    fieldInput.classList.remove('is-invalid');
    fieldInput.classList.add('is-valid');

    // Enable field type again
    if (fieldTypeSelect) fieldTypeSelect.disabled = false;

    // Enable create button only if no duplicates exist
    const hasAnyDuplicate = Array.from(document.querySelectorAll('.fieldName')).some(input => {
      const name = input.value.trim();
      if (!name) return false;
      const matches = allRows.filter(row => row.querySelector('.fieldName')?.value.trim() === name);
      return matches.length > 1;
    });

    document.getElementById('createButton').disabled = hasAnyDuplicate;
    return false;
  }
}



    // ===== Check Field Name Availability Server-Side (ONLY for multifield) =====
    const checkFieldNameAvailability = debounce((fieldInput) => {
        const fieldName = fieldInput.value.trim();
        if (!fieldName) {
            validateFormFields();
            return;
        }

        // Only perform server-side existence check for multifield types
        const row = fieldInput.closest('.field-row, .nested-row');
        const rowType = row?.querySelector('.fieldType')?.value;
        if (rowType !== 'multifield') {
            // clear any server error flag/message
            const errDiv = fieldInput.nextElementSibling || createFieldErrorDiv(fieldInput);
            errDiv.dataset.serverError = 'false';
            if (!errDiv.innerText) {
                fieldInput.classList.remove('is-invalid');
                fieldInput.classList.remove('is-valid');
            }
            validateFormFields();
            return;
        }

        // If duplicate (local) check already detects problem, skip server call
        if (checkDuplicateFieldNameWithinComponent(fieldInput)) {
            validateFormFields();
            return;
        }

        // server call: checkChildJavaClassName
        fetch(`/checkChildJavaClassName?projectName=${encodeURIComponent(projectName)}&fieldName=${encodeURIComponent(fieldName)}`)
            .then(res => res.json())
            .then((exists) => {
                const errorDiv = fieldInput.nextElementSibling || createFieldErrorDiv(fieldInput);
                errorDiv.dataset.serverError = exists ? 'true' : 'false';
                if (exists) {
                    errorDiv.innerText = '⚠️ Field name already exists in another component!';
                    errorDiv.classList.add('text-danger');
                    fieldInput.classList.add('is-invalid');
                    fieldInput.classList.remove('is-valid');
                    createButton.disabled = true;
                } else {
                    // Only clear server error text if no duplicate message is present
                    if (errorDiv.innerText.includes('already exists')) {
                        errorDiv.innerText = '';
                    }
                    fieldInput.classList.remove('is-invalid');
                    fieldInput.classList.add('is-valid');
                    validateFormFields();
                }
            })
            .catch(() => {
                const errorDiv = fieldInput.nextElementSibling || createFieldErrorDiv(fieldInput);
                errorDiv.dataset.serverError = 'true';
                errorDiv.innerText = '⚠️ Server error while checking field name!';
                errorDiv.classList.add('text-danger');
                fieldInput.classList.add('is-invalid');
                createButton.disabled = true;
            });
    }, 400);

    function createFieldErrorDiv(input) {
        const existing = input.nextElementSibling;
        if (existing && existing.classList.contains('fieldError')) return existing;
        const div = document.createElement('div');
        div.className = 'fieldError text-danger mt-1';
        div.dataset.serverError = 'false';
        input.insertAdjacentElement('afterend', div);
        return div;
    }

    // ===== Field Type Handling =====
    window.handleFieldTypeChange = function (select) {
        if (!select) return;
        const row = select.closest('.field-row, .nested-row');
        const type = select.value;
        const opt = row.querySelector('.options-container');
        const nested = row.querySelector('.nested-container');

        if (opt) opt.innerHTML = '';
        if (nested) nested.innerHTML = '';

        if (["select", "multiselect", "checkboxgroup", "radiogroup"].includes(type)) {
            const addBtn = document.createElement('button');
            addBtn.type = 'button';
            addBtn.className = 'btn btn-sm btn-secondary mb-2';
            addBtn.textContent = 'Add Option';
            addBtn.addEventListener('click', () => addTextValueRow(addBtn));
            if (opt) opt.appendChild(addBtn);
            if (opt) addTextValueRow(addBtn);
        } else if (type === 'multifield' || type === 'tabs') {
            const addBtn = document.createElement('button');
            addBtn.type = 'button';
            addBtn.className = 'btn btn-sm btn-secondary mb-2';
            addBtn.textContent = 'Add Field';
            addBtn.addEventListener('click', () => addNestedFieldRow(addBtn));
            if (nested) nested.appendChild(addBtn);
            if (nested) addNestedFieldRow(addBtn);
        }

        updateIndexes();
    };

    // ===== Add/Remove Fields =====
    window.addFieldRow = function () {
        const container = document.getElementById('fieldsContainer');
        const row = createBaseRow(false);
        const action = row.querySelector('.action-col');
        if (action) action.innerHTML = '<button type="button" class="btn btn-danger" onclick="removeFieldRow(this)">-</button>';
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

    window.removeNestedFieldRow = function (btn) {
        btn.closest('.nested-row').remove();
        updateIndexes();
        validateFormFields();
    };

    function addNestedFieldRow(btn) {
        const container = btn.closest('.nested-container');
        const parent = container.closest('.field-row, .nested-row');
        const level = parseInt(parent.dataset.level || 0) + 1;
        const parentType = parent.querySelector('.fieldType')?.value;

        // Check nested restriction
        if ((parentType === 'multifield' || parentType === 'tabs') && level > 2) {
            showNestedRestrictionError(container);
            validateFormFields();
            return;
        }

        const row = createBaseRow(true, level);
        container.insertBefore(row, btn);
        updateIndexes();
        validateFormFields();
    }

    function showNestedRestrictionError(container) {
        let msg = container.querySelector('.nestedRestrictionMsg');
        if (!msg) {
            msg = document.createElement('div');
            msg.className = 'alert alert-danger nestedRestrictionMsg mt-2';
            msg.innerText = '🚫 Error: Nested multifields/tabs inside another multifield are not allowed.';
            container.prepend(msg);
        }
        createButton.disabled = true;
    }

    // ===== Add/Remove Option Row =====
    function addTextValueRow(btn, text = '', value = '') {
        const container = btn.closest('.options-container');
        const div = document.createElement('div');
        div.className = 'option-row nested-row input-group mb-2';
        div.innerHTML = `
            <input type="text" class="form-control optionText" placeholder="Text" value="${escapeHtml(text)}" required>
            <input type="text" class="form-control optionValue" placeholder="Value" value="${escapeHtml(value)}" required>
            <button type="button" class="btn btn-danger" onclick="removeOptionRow(this)">-</button>`;
        if (container) container.insertBefore(div, btn);
        updateIndexes();
        validateFormFields();
    }

    window.removeOptionRow = function (btn) {
        const parent = btn.parentElement;
        if (parent) parent.remove();
        updateIndexes();
        validateFormFields();
    };

    function escapeHtml(str) {
        if (str === undefined || str === null) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
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

        const nestedContainer = row.querySelector(':scope > .nested-container');
        if (nestedContainer) {
            const nestedRows = nestedContainer.querySelectorAll(':scope > .nested-row');
            let nestedFieldIndex = 0;
            nestedRows.forEach((nrow) => {
                const nestedLabel = nrow.querySelector('.fieldLabel');
                if (nestedLabel) {
                    setRowNames(nrow, `${prefix}.nestedFields[${nestedFieldIndex}]`);
                    nestedFieldIndex++;
                }
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
                    actionCol.innerHTML = '<button type="button" class="btn btn-danger" onclick="removeFieldRow(this)">-</button>';
                }
            }
        });
    }

    // ===== Component Name Validation (forbidden words + backend check) =====
    componentNameInput.addEventListener("input", function () {
        const cleaned = componentNameInput.value.replace(/[^A-Za-z0-9_]/g, "");
        if (componentNameInput.value !== cleaned) {
            componentNameInput.value = cleaned;
        }

        const val = componentNameInput.value.trim().toLowerCase();
        if (!val) {
            errorDiv.innerText = '';
            errorDiv.classList.remove('text-danger', 'text-success');
            componentNameInput.classList.remove('is-invalid');
            validateFormFields();
            return;
        }

        // Forbidden words check
        if (forbiddenWords.includes(val)) {
            errorDiv.innerText = `⚠️ The component name cannot be "${val}".`;
            errorDiv.classList.add('text-danger');
            errorDiv.classList.remove('text-success');
            componentNameInput.classList.add('is-invalid');
            createButton.disabled = true;
            return;
        }

        checkComponentNameAvailability();
    });

    const checkComponentNameAvailability = debounce(() => {
        const componentName = componentNameInput.value.trim();
        if (!componentName) {
            errorDiv.innerText = '';
            errorDiv.classList.remove('text-danger', 'text-success');
            componentNameInput.classList.remove('is-invalid');
            validateFormFields();
            return;
        }

        // Keep original behaviour: call your existing endpoint
        fetch(`/check-componentName/${projectName}?componentName=${encodeURIComponent(componentName)}`)
            .then(res => res.json())
            .then((isAvailable) => {
                // Assuming backend returns false if exists (as per your logs)
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

    // ===== Validate Form =====
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

        const rows = document.querySelectorAll('#fieldsContainer .field-row, #fieldsContainer .nested-row');
        if (mode === 'new' && rows.length === 0) {
            createButton.disabled = true;
            return;
        }

        const fieldNames = new Set();
        for (let row of rows) {
            const label = row.querySelector('.fieldLabel');
            const fname = row.querySelector('.fieldName');
            const type = row.querySelector('.fieldType');

            if (label && !label.value.trim()) {
                createButton.disabled = true;
                return;
            }

            if (fname && (!fname.value.trim() || fname.classList.contains('is-invalid'))) {
                createButton.disabled = true;
                return;
            }

            if (type && !type.value) {
                createButton.disabled = true;
                return;
            }

            if (row.querySelector('.nestedRestrictionMsg')) {
                createButton.disabled = true;
                return;
            }

            if (fname) {
                // Only check duplicates for multifields
                if (type.value === 'multifield' && fieldNames.has(fname.value.trim())) {
                    fname.classList.add('is-invalid');
                    createButton.disabled = true;
                    return;
                } else {
                    fieldNames.add(fname.value.trim());
                }
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

    // ===== Manual FieldName Edit Check =====
    document.addEventListener('input', function (e) {
        if (e.target.classList.contains('fieldName')) {
            // run local duplicate check (it will only flag if type == multifield)
            checkDuplicateFieldNameWithinComponent(e.target);

            // only run server-side check when type === 'multifield'
            if (isFieldTypeMultifield(e.target)) {
                checkFieldNameAvailability(e.target);
            } else {
                // if not multifield clear any server error
                const err = e.target.nextElementSibling;
                if (err) {
                    err.dataset.serverError = 'false';
                    if (err.innerText && !err.innerText.includes('Duplicate')) {
                        err.innerText = '';
                    }
                }
                e.target.classList.remove('is-invalid');
                e.target.classList.remove('is-valid');
            }
        }
    });

    updateIndexes();

    // ===== Edit Mode Loading =====
    if (window.editMode) loadComponentData(window.componentData || {});

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

            // Try fetching parent tabs (so UI matches)
            fetchParentTabsAndFields(data.superType);
        } else {
            modeSelect.value = 'new';
            toggleSuperType();
        }

        const container = document.getElementById('fieldsContainer');
        container.innerHTML = '';

        if (Array.isArray(data.fields)) {
            data.fields.forEach((f) => {
                const row = createBaseRow(false);
                const action = row.querySelector('.action-col');
                if (action) action.innerHTML = '<button type="button" class="btn btn-danger" onclick="removeFieldRow(this)">-</button>';
                container.appendChild(row);
                populateFieldRow(row, f);
            });
        }

        updateIndexes();
        validateFormFields();
    }

    function populateFieldRow(row, field, level = 0) {
        if (!row || !field) return;

        const labelEl = row.querySelector('.fieldLabel');
        const nameEl = row.querySelector('.fieldName');
        const typeEl = row.querySelector('.fieldType');

        // set type first (so validation functions know the type)
        if (typeEl) typeEl.value = field.fieldType || '';

        if (labelEl) labelEl.value = field.fieldLabel || '';
        if (nameEl) {
            nameEl.value = field.fieldName || '';
            // run duplicate & server checks only when appropriate
            checkDuplicateFieldNameWithinComponent(nameEl);
            if (isFieldTypeMultifield(nameEl)) {
                checkFieldNameAvailability(nameEl);
            }
        }

        const optionsContainer = row.querySelector('.options-container');
        const nestedContainer = row.querySelector('.nested-container');
        if (optionsContainer) optionsContainer.innerHTML = '';
        if (nestedContainer) nestedContainer.innerHTML = '';

        if (
            ["select", "multiselect", "checkboxgroup", "radiogroup"].includes(field.fieldType) &&
            Array.isArray(field.options)
        ) {
            field.options.forEach((opt) => {
                const div = document.createElement('div');
                div.className = 'option-row nested-row input-group mb-2';
                div.innerHTML = `
                    <input type="text" class="form-control optionText" placeholder="Text" value="${escapeHtml(opt.text)}" required>
                    <input type="text" class="form-control optionValue" placeholder="Value" value="${escapeHtml(opt.value)}" required>
                    <button type="button" class="btn btn-danger" onclick="removeOptionRow(this)">-</button>`;
                optionsContainer.appendChild(div);
            });
        } else if (
            (field.fieldType === 'multifield' || field.fieldType === 'tabs') &&
            Array.isArray(field.nestedFields)
        ) {
            field.nestedFields.forEach((nf) => {
                const rowNested = createBaseRow(true, level + 1);
                nestedContainer.appendChild(rowNested);
                populateFieldRow(rowNested, nf, level + 1);
            });
        }
    }

    // ===== Fetch parent tabs and fields from backend and populate fieldsContainer =====
    function fetchParentTabsAndFields(superType) {
        if (!superType) return;
        fetch('/checkTabs', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ projectName: projectName, superType: superType })
        })
            .then(res => res.json())
            .then((data) => {
                // Show parent tabs debug UI (if exists)
                if (parentTabsContainer) {
                    parentTabsContainer.innerHTML = '';
                }

                // If backend returns data.fields (detailed fields), populate using populateFieldRow
                if (data && Array.isArray(data.fields) && data.fields.length > 0) {
                    fieldsContainer.innerHTML = '';
                    data.fields.forEach(f => {
                        const row = createBaseRow(false);
                        const action = row.querySelector('.action-col');
                        if (action) action.innerHTML = '<button type="button" class="btn btn-danger" onclick="removeFieldRow(this)">-</button>';
                        fieldsContainer.appendChild(row);
                        populateFieldRow(row, f);
                    });
                } else if (data && data.hasTabs && Array.isArray(data.tabs) && data.tabs.length > 0) {
                    // If backend returned only tabs list, convert each tab into a 'tabs' field row so it is visible/editable
                    fieldsContainer.innerHTML = '';
                    data.tabs.forEach(tabName => {
                        const row = createBaseRow(false);
                        const action = row.querySelector('.action-col');
                        if (action) action.innerHTML = '<button type="button" class="btn btn-danger" onclick="removeFieldRow(this)">-</button>';
                        fieldsContainer.appendChild(row);

                        const labelEl = row.querySelector('.fieldLabel');
                        const nameEl = row.querySelector('.fieldName');
                        const typeEl = row.querySelector('.fieldType');

                        if (labelEl) labelEl.value = tabName;
                        if (nameEl) {
                            // make a simple camelCase name for the tab
                            const camel = tabName.replace(/[^a-zA-Z0-9 ]/g, '')
                                .split(/\s+/)
                                .map((w, i) => i === 0 ? w.toLowerCase() : w.charAt(0).toUpperCase() + w.slice(1))
                                .join('');
                            nameEl.value = camel || tabName.toLowerCase();
                        }
                        if (typeEl) {
                            typeEl.value = 'tabs';
                            // ensure nested container gets created
                            handleFieldTypeChange(typeEl);
                        }

                        // Optionally show parent tabs container
                        if (parentTabsContainer) {
                            const tag = document.createElement('div');
                            tag.className = 'alert alert-info mt-1';
                            tag.textContent = `Parent Tab: ${tabName}`;
                            parentTabsContainer.appendChild(tag);
                        }
                    });
                } else {
                    // no useful data returned
                    // Keep existing fields or show message
                    if (parentTabsContainer) {
                        parentTabsContainer.innerHTML = '<div class="alert alert-secondary mt-2">No parent tabs/fields found.</div>';
                    }
                }

                updateIndexes();
                validateFormFields();
            })
            .catch((err) => {
                console.error('Error fetching parent tabs/fields:', err);
                if (parentTabsContainer) {
                    parentTabsContainer.innerHTML = '<div class="alert alert-danger mt-2">Failed to fetch parent tabs/fields.</div>';
                }
            });
    }

    // Trigger fetch when superType changes (only when in extend mode)
    if (superTypeSelect) {
        superTypeSelect.addEventListener('change', function () {
            if (modeSelect.value === 'extend') {
                fetchParentTabsAndFields(this.value);
            }
        });
    }

    // Initial updateIndexes call to set names if template rows already exist
    updateIndexes();
});