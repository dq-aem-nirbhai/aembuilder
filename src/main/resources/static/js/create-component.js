document.addEventListener("DOMContentLoaded", function () {
  let fieldIndex = document.querySelectorAll('.field-row').length;

  const componentNameInput = document.getElementById('componentName');
  const errorDiv = document.getElementById('nameError');
  const createButton = document.getElementById('createButton');
  const projectName = document.getElementById("projectName").value;
  const originalNameElem = document.getElementById('originalName');
  const originalName = originalNameElem ? originalNameElem.value.trim() : null;

  function debounce(func, delay) {
    let timer;
    return function (...args) {
      clearTimeout(timer);
      timer = setTimeout(() => func.apply(this, args), delay);
    };
  }

  const checkComponentNameAvailability = debounce(() => {
    const componentName = componentNameInput.value.trim();
    if (originalName && componentName === originalName) {
      errorDiv.innerText = "";
      errorDiv.classList.remove('text-danger', 'text-success');
      componentNameInput.classList.remove('is-invalid');
      validateFormFields();
      return;
    }
    if (!componentName) {
      errorDiv.innerText = "";
      errorDiv.classList.remove('text-danger', 'text-success');
      componentNameInput.classList.remove('is-invalid');
      validateFormFields();
      return;
    }

    fetch(`/check-componentName/${projectName}?componentName=${encodeURIComponent(componentName)}`)
      .then(response => response.json())
      .then(isAvailable => {
        if (isAvailable === false) {
          //  Exact match found
          errorDiv.innerText = "⚠️ Component name already exists. Please choose another.";
          errorDiv.classList.add('text-danger');
          errorDiv.classList.remove('text-success');
          componentNameInput.classList.add('is-invalid');
          createButton.disabled = true;
        } else {
          // Name is available
          errorDiv.innerText = "✅ Component name is available.";
          errorDiv.classList.remove('text-danger');
          errorDiv.classList.add('text-success');
          componentNameInput.classList.remove('is-invalid');
          validateFormFields();
        }
      })
      .catch(err => {
        console.error("Error checking component name:", err);
        errorDiv.innerText = "⚠️ Server error while checking component name.";
        errorDiv.classList.add('text-danger');
        errorDiv.classList.remove('text-success');
        componentNameInput.classList.add('is-invalid');
        createButton.disabled = true;
      });
  }, 400);

  componentNameInput.addEventListener('input', checkComponentNameAvailability);

  window.autoFillFieldName = function (labelInput) {
    const row = labelInput.closest('.field-row');
    const nameInput = row.querySelector('.fieldName');
    const labelValue = labelInput.value.trim();

    const camelCase = labelValue
      .replace(/[^a-zA-Z0-9 ]/g, '')
      .split(/\s+/)
      .map((word, i) => i === 0 ? word.toLowerCase() : word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join('');

    nameInput.value = camelCase;
    validateFormFields();
  };

  window.addFieldRow = function () {
    const container = document.getElementById('fieldsContainer');
    const firstRow = container.querySelector('.field-row');
    const newRow = firstRow.cloneNode(true);

    newRow.querySelectorAll('input').forEach(input => input.value = "");
    newRow.querySelectorAll('select').forEach(select => select.value = "");

    newRow.querySelector('.fieldLabel').setAttribute('name', `fields[${fieldIndex}].fieldLabel`);
    newRow.querySelector('.fieldName').setAttribute('name', `fields[${fieldIndex}].fieldName`);
    newRow.querySelector('.fieldType').setAttribute('name', `fields[${fieldIndex}].fieldType`);

    const colAuto = newRow.querySelector('.col-auto');
    colAuto.innerHTML = `<button type="button" class="btn btn-danger" onclick="removeFieldRow(this)">-</button>`;

    newRow.classList.add('animate__animated', 'animate__fadeIn');
    container.appendChild(newRow);
    fieldIndex++;
  };

  window.removeFieldRow = function (button) {
    const row = button.closest('.field-row');
    const container = document.getElementById('fieldsContainer');
    if (container.querySelectorAll('.field-row').length > 1) {
      row.classList.add('removed');
      setTimeout(() => row.remove(), 300);
    }
    validateFormFields();
  };

  window.validateFormFields = function () {
    const componentName = componentNameInput.value.trim();
    const componentGroup = document.getElementById('componentGroup').value;

    if (!componentName || !componentGroup || componentNameInput.classList.contains('is-invalid')) {
      createButton.disabled = true;
      return;
    }

    const rows = document.querySelectorAll('.field-row');
    for (let row of rows) {
      const label = row.querySelector('.fieldLabel').value.trim();
      const name = row.querySelector('.fieldName').value.trim();
      const type = row.querySelector('.fieldType').value;
      if (!label || !name || !type) {
        createButton.disabled = true;
        return;
      }
    }

    createButton.disabled = false;
  };

  document.addEventListener('input', validateFormFields);
});
