// /js/project-details.js

/* global bootstrap */

let selectedComponents = [];
let selectedTemplates = [];

// ---------------------------------------------
// GET PROJECT NAME
// ---------------------------------------------
function getProjectName() {
    const el = document.getElementById('componentModal');
    const projectName = el ? el.getAttribute('data-project') : '';
    if (!projectName) {
        alert("Project name missing, cannot load data.");
    }
    return projectName;
}

// ---------------------------------------------
// DEPLOY BUTTON DISABLE/ENABLE
// ---------------------------------------------
function setDeployDisabled(disabled) {
    const btn = document.getElementById('deployBtn');
    if (!btn) return;
    if (disabled) {
        btn.classList.add('disabled');
        btn.setAttribute('aria-disabled', 'true');
    } else {
        btn.classList.remove('disabled');
        btn.removeAttribute('aria-disabled');
    }
}

// ---------------------------------------------
// ENABLE/DISABLE "ADD SELECTION" BUTTON
// ---------------------------------------------
function updateAddSelectionButton(type) {
    const listId = type === 'component' ? 'componentList' : 'templateList';
    const btnId  = type === 'component' ? 'addComponentBtn' : 'addTemplateBtn';

    const btn = document.getElementById(btnId);
    if (!btn) return;

    const anyChecked = document.querySelector(
        `#${listId} input[type=checkbox]:checked:not(:disabled)`
    );

    if (anyChecked) {
        btn.disabled = false;
        btn.classList.remove("disabled");
    } else {
        btn.disabled = true;
        btn.classList.add("disabled");
    }
}


// ---------------------------------------------
// RENDER LIST INSIDE MODAL
// ---------------------------------------------
function renderList(containerId, dataList, selectedList, type) {
    const container = document.getElementById(containerId);
    if (!container) return;
    container.innerHTML = '';

    const unique = (dataList && Array.isArray(dataList.unique)) ? dataList.unique
                 : (Array.isArray(dataList) ? dataList : []);
    const duplicate = (dataList && Array.isArray(dataList.duplicate)) ? dataList.duplicate : [];

    const allItems = [...new Set([...unique, ...duplicate, ...selectedList])];

    if (allItems.length === 0) {
        container.innerHTML = '<div class="text-muted">No items available.</div>';
        return;
    }

    allItems.forEach(item => {
        const isAlreadyAdded = selectedList.includes(item);
        const isDuplicate = duplicate.includes(item);

        let labelSuffix = '';
        let isDisabled = false;
        let isChecked = false;

        if (isAlreadyAdded) {
            labelSuffix = ' (Already Added)';
            isDisabled = true;
            isChecked = true;
        } else if (isDuplicate) {
            labelSuffix = ' (Exists)';
            isDisabled = true;
        }

        const escapedItem = String(item).replace(/"/g, '&quot;');

        container.insertAdjacentHTML('beforeend', `
            <div class="col">
                <div class="form-check">
                    <input class="form-check-input" type="checkbox" value="${escapedItem}" 
                        ${isChecked ? 'checked' : ''} 
                        ${isDisabled ? 'disabled' : ''}>
                    <label class="form-check-label ${isDisabled ? 'text-muted' : ''}">
                        ${item}${labelSuffix}
                    </label>
                </div>
            </div>`);
    });

    // 🔥 Enable/Disable button based on initial state
    updateAddSelectionButton(type);

    // 🔥 Add listener to each checkbox
    container.querySelectorAll("input[type=checkbox]").forEach(cb => {
        cb.addEventListener("change", () => updateAddSelectionButton(type));
    });
}


// ---------------------------------------------
// OPEN MODALS
// ---------------------------------------------
function openComponentModal() {
    const projectName = getProjectName();
    if (!projectName) return;
    fetch(`/fetch-components/${projectName}`)
        .then(res => res.json())
        .then(data => {
            renderList('componentList', data, selectedComponents, 'component');
            new bootstrap.Modal(document.getElementById('componentModal')).show();
        })
        .catch(() => alert('Unable to load components!'));
}

function openTemplateModal() {
    const projectName = getProjectName();
    if (!projectName) return;
    fetch(`/fetch-templates/${projectName}`)
        .then(res => res.json())
        .then(data => {
            renderList('templateList', data, selectedTemplates, 'template');
            new bootstrap.Modal(document.getElementById('templateModal')).show();
        })
        .catch(() => alert('Unable to load templates!'));
}


// ---------------------------------------------
// ADD SELECTED ITEMS (COMPONENT / TEMPLATE)
// ---------------------------------------------
function addSelected(type) {
    const listId = type === 'component' ? 'componentList' : 'templateList';
    const containerId = type === 'component' ? 'newComponentsList' : 'newTemplatesList';
    const mainContainerId = type === 'component' ? 'newComponentsContainer' : 'newTemplatesContainer';
    const list = type === 'component' ? selectedComponents : selectedTemplates;

    const checkedInputs = Array.from(document.querySelectorAll(
        `#${listId} input[type=checkbox]:checked:not(:disabled)`
    ));
    const selected = checkedInputs.map(cb => cb.value);

    const container = document.getElementById(containerId);

    selected.forEach(item => {
        if (!list.includes(item)) list.push(item);

        const col = document.createElement('div');
        col.className = 'col';
        col.innerHTML = `
            <div class="border rounded p-2 bg-light text-center shadow-sm removable-item" data-item-name="${item}">
                <span class="d-block">${item}</span>
                <button class="btn btn-link p-0 mt-1 text-danger remove-btn" onclick="removeItem('${item}', '${type}')">&times;</button>
            </div>`;
        container.appendChild(col);
    });

    document.getElementById(mainContainerId).style.display = 'block';

    showSave();

    // hide modal
    const modalEl = document.getElementById(type + 'Modal');
    const instance = bootstrap.Modal.getInstance(modalEl);
    instance.hide();
}

function addSelectedComponents() { addSelected('component'); }
function addSelectedTemplates() { addSelected('template'); }


// ---------------------------------------------
// REMOVE ITEM
// ---------------------------------------------
function removeItem(name, type) {
    const list = type === 'component' ? selectedComponents : selectedTemplates;
    const idx = list.indexOf(name);
    if (idx !== -1) list.splice(idx, 1);

    const containerId = type === 'component' ? 'newComponentsList' : 'newTemplatesList';
    const container = document.getElementById(containerId);

    const itemEl = container.querySelector(`[data-item-name="${name}"]`);
    if (itemEl) itemEl.closest('.col').remove();

    const mainContainerId = type === 'component' ? 'newComponentsContainer' : 'newTemplatesContainer';
    if (list.length === 0) document.getElementById(mainContainerId).style.display = 'none';

    checkSaveVisibility();
}


// ---------------------------------------------
// SAVE BUTTON VISIBILITY
// ---------------------------------------------
function showSave() {
    const saveBtn = document.getElementById('saveBtn');
    if (saveBtn) saveBtn.style.display = 'inline-block';
    setDeployDisabled(true);
}

function checkSaveVisibility() {
    const saveBtn = document.getElementById('saveBtn');
    if (selectedComponents.length === 0 && selectedTemplates.length === 0) {
        if (saveBtn) saveBtn.style.display = 'none';
        setDeployDisabled(false);
    }
}


// ---------------------------------------------
// SAVE ALL
// ---------------------------------------------
function saveAll() {
    const projectName = getProjectName();
    if (!projectName) return;

    const promises = [];

    if (selectedComponents.length > 0) {
        promises.push(fetch(`/add-components/${projectName}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(selectedComponents)
        }));
    }

    if (selectedTemplates.length > 0) {
        promises.push(fetch(`/add-template/${projectName}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(selectedTemplates)
        }));
    }

    if (promises.length === 0) return;

    Promise.all(promises)
        .then(responses => {
            if (responses.some(r => !r.ok)) throw new Error("Error");

            const msg = document.getElementById('successMessage');
            msg.style.display = 'block';

            setTimeout(() => {
                msg.style.display = 'none';
                window.location.reload();
            }, 1200);
        })
        .catch(() => alert('Save failed!'));
}


// ---------------------------------------------
// DEPLOY FUNCTION
// ---------------------------------------------
function startDeploy() {
    const projectName = getProjectName();
    if (!projectName) return false;

    const select = document.getElementById('deployTypeSelect');
    const type = select.value;

    window.location.href = `/${projectName}/deploy?type=${type}`;
    return false;
}


// ---------------------------------------------
// FLASH MESSAGE AUTO REMOVE
// ---------------------------------------------
window.addEventListener("DOMContentLoaded", () => {
    const flash = document.getElementById("flashMessage");
    if (flash) {
        setTimeout(() => {
            flash.remove();
        }, 5000);
    }
});
