// /js/project-details.js

/* global bootstrap */

let selectedComponents = [];
let selectedTemplates = [];

function getProjectName() {
    const el = document.getElementById('componentModal');
    const projectName = el ? el.getAttribute('data-project') : '';
    if (!projectName) {
        alert("Project name missing, cannot load data.");
    }
    return projectName;
}

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

function renderList(containerId, dataList, selectedList, type) {
    const container = document.getElementById(containerId);
    if (!container) return;
    container.innerHTML = '';

    // Expecting dataList = { unique: [...], duplicate: [...] } OR an array fallback
    const unique = (dataList && Array.isArray(dataList.unique)) ? dataList.unique
                 : (Array.isArray(dataList) ? dataList : []);
    const duplicate = (dataList && Array.isArray(dataList.duplicate)) ? dataList.duplicate : [];

    // combine unique+duplicate+currently selected
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

        // Use data attributes for better safety (avoid duplicate IDs)
        const escapedItem = String(item).replace(/"/g, '&quot;');

        container.insertAdjacentHTML('beforeend', `
            <div class="col">
                <div class="form-check">
                    <input class="form-check-input" type="checkbox" value="${escapedItem}" ${isChecked ? 'checked' : ''} ${isDisabled ? 'disabled' : ''} id="${type}-${escapedItem}">
                    <label class="form-check-label ${isDisabled ? 'text-muted' : ''}" for="${type}-${escapedItem}">${item}${labelSuffix}</label>
                </div>
            </div>`);
    });
}

function openComponentModal() {
    const projectName = getProjectName();
    if (!projectName) return;
    fetch(`/fetch-components/${projectName}`)
        .then(res => {
            if (!res.ok) throw new Error('Failed to fetch components');
            return res.json();
        })
        .then(data => {
            // data should be { unique: [...], duplicate: [...] } or array
            renderList('componentList', data, selectedComponents, 'component');
            const modal = new bootstrap.Modal(document.getElementById('componentModal'));
            modal.show();
        })
        .catch(err => {
            console.error(err);
            alert('Unable to load components. Please try again.');
        });
}

function openTemplateModal() {
    const projectName = getProjectName();
    if (!projectName) return;
    fetch(`/fetch-templates/${projectName}`)
        .then(res => {
            if (!res.ok) throw new Error('Failed to fetch templates');
            return res.json();
        })
        .then(data => {
            renderList('templateList', data, selectedTemplates, 'template');
            const modal = new bootstrap.Modal(document.getElementById('templateModal'));
            modal.show();
        })
        .catch(err => {
            console.error(err);
            alert('Unable to load templates. Please try again.');
        });
}

function addSelected(type) {
    const listId = type === 'component' ? 'componentList' : 'templateList';
    const checkedInputs = Array.from(document.querySelectorAll(`#${listId} input[type=checkbox]:checked:not(:disabled)`));
    const selected = checkedInputs.map(cb => cb.value);

    const containerId = type === 'component' ? 'newComponentsList' : 'newTemplatesList';
    const container = document.getElementById(containerId);
    const list = type === 'component' ? selectedComponents : selectedTemplates;

    if (!container) {
        console.warn('Container not found for', containerId);
    }

    selected.forEach(item => {
        if (!list.includes(item)) list.push(item);

        // create UI tile for added item
        const col = document.createElement('div');
        col.className = 'col';
        // set data-item-name attribute for easy removal
        col.innerHTML = `
            <div class="border rounded p-2 bg-light text-center shadow-sm removable-item" data-item-name="${item}">
                <span class="d-block">${item}</span>
                <button class="btn btn-link p-0 mt-1 text-danger remove-btn" onclick="removeItem('${item}', '${type}')">&times;</button>
            </div>`;
        if (container) container.appendChild(col);
    });

    // show containers
    const mainContainerId = type === 'component' ? 'newComponentsContainer' : 'newTemplatesContainer';
    const mainContainer = document.getElementById(mainContainerId);
    if (mainContainer) mainContainer.style.display = 'block';

    showSave();

    // hide modal instance
    const modalEl = document.getElementById(type + 'Modal');
    const instance = bootstrap.Modal.getInstance(modalEl);
    if (instance) instance.hide();
}

function addSelectedComponents() { addSelected('component'); }
function addSelectedTemplates() { addSelected('template'); }

function removeItem(name, type) {
    const list = type === 'component' ? selectedComponents : selectedTemplates;
    const idx = list.indexOf(name);
    if (idx !== -1) list.splice(idx, 1);

    const containerId = type === 'component' ? 'newComponentsList' : 'newTemplatesList';
    const container = document.getElementById(containerId);
    if (!container) return;

    // find element by data-item-name (use attribute selector)
    const escaped = CSS && CSS.escape ? CSS.escape(name) : name;
    const itemEl = container.querySelector(`[data-item-name="${escaped}"]`);
    if (itemEl) {
        const col = itemEl.closest('.col') || itemEl;
        col.remove();
    } else {
        // fallback: try to find by text match
        const fallback = Array.from(container.querySelectorAll('.removable-item')).find(el => el.textContent && el.textContent.includes(name));
        if (fallback) fallback.closest('.col').remove();
    }

    const mainContainerId = type === 'component' ? 'newComponentsContainer' : 'newTemplatesContainer';
    const mainContainer = document.getElementById(mainContainerId);
    if (list.length === 0 && mainContainer) {
        mainContainer.style.display = 'none';
    }
    checkSaveVisibility();
}

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

    if (promises.length === 0) {
        checkSaveVisibility();
        return;
    }

    Promise.all(promises)
        .then(responses => {
            const anyBad = responses.some(r => !r.ok);
            if (anyBad) throw new Error('One or more requests failed');

            // show success message and reload
            const msg = document.getElementById('successMessage');
            if (msg) {
                msg.style.display = 'block';
                setTimeout(() => {
                    msg.style.display = 'none';
                    window.location.reload();
                }, 1200);
            } else {
                // fallback
                alert('Save successful!');
                window.location.reload();
            }
        })
        .catch(err => {
            console.error(err);
            alert('Save failed. Please check server logs and try again.');
            setDeployDisabled(false);
        });
}

/**
 * New: starts deploy navigation with selected deploy type.
 * It will respect restricted-branch checks (same UX as other actions).
 */
function startDeploy() {
    const projectName = getProjectName();
    if (!projectName) return false;

    // respect branch restrictions
    if (!checkRestrictedBranch('deploy this project')) {
        return false;
    }

    const select = document.getElementById('deployTypeSelect');
    const type = select ? select.value : 'full';

    // navigate to deploy page, which will render deploy-logs and pass deployType to SSE endpoint
    const url = `/${encodeURIComponent(projectName)}/deploy?type=${encodeURIComponent(type)}`;
    // open in same tab (original behavior). If you prefer new tab, use window.open(url, '_blank').
    window.location.href = url;
    return false; // prevent default anchor navigation
}

window.addEventListener("DOMContentLoaded", () => {
    // hide flash message after 5s
    const flash = document.getElementById("flashMessage");
    if (flash) {
        setTimeout(() => {
            try { flash.remove(); } catch (e) {}
        }, 5000);
    }
});
