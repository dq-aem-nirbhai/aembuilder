document.addEventListener("DOMContentLoaded", function () {
  let fieldIndex = typeof existingFieldCount !== 'undefined' ? existingFieldCount : 1;

  const createButton = document.getElementById('createButton');
  const componentNameInput = document.getElementById('componentName');

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
    const componentGroup = document.getElementById('componentGroup').value;
    if (!componentGroup) {
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
  validateFormFields();
});
