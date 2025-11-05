
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

    const unique = (dataList && Array.isArray(dataList.unique)) ? dataList.unique : [];
    const duplicate = (dataList && Array.isArray(dataList.duplicate)) ? dataList.duplicate : [];
    const allItems = [...new Set([...unique, ...duplicate, ...selectedList])];

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

        container.insertAdjacentHTML('beforeend', `
            <div class="col">
                <div class="form-check">
                    <input class="form-check-input" type="checkbox" value="${item}" ${isChecked ? 'checked' : ''} ${isDisabled ? 'disabled' : ''}>
                    <label class="form-check-label ${isDisabled ? 'text-muted' : ''}">${item}${labelSuffix}</label>
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
            renderList('componentList', data, selectedComponents, 'component');
            new bootstrap.Modal(document.getElementById('componentModal')).show();
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
            new bootstrap.Modal(document.getElementById('templateModal')).show();
        })
        .catch(err => {
            console.error(err);
            alert('Unable to load templates. Please try again.');
        });
}

function addSelected(type) {
    const listId = type === 'component' ? 'componentList' : 'templateList';
    const selected = Array.from(document.querySelectorAll(`#${listId} input[type=checkbox]:checked:not(:disabled)`))
        .map(cb => cb.value);

    const containerId = type === 'component' ? 'newComponentsList' : 'newTemplatesList';
    const container = document.getElementById(containerId);
    const list = type === 'component' ? selectedComponents : selectedTemplates;

    selected.forEach(item => {
        if (!list.includes(item)) list.push(item);
        const col = document.createElement('div');
        col.className = 'col';
        col.innerHTML = `
            <div class="border rounded p-2 bg-light text-center shadow-sm removable-item" data-item-name="${item}">
                ${item}
                <span class="remove-btn text-danger" onclick="removeItem('${item}', '${type}')">&times;</span>
            </div>`;
        container.appendChild(col);
    });

    document.getElementById(type === 'component' ? 'newComponentsContainer' : 'newTemplatesContainer').style.display = 'block';
    showSave();
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
    const itemEl = container.querySelector(`[data-item-name="${CSS && CSS.escape ? CSS.escape(name) : name}"]`);
    if (itemEl) {
        const col = itemEl.closest('.col') || itemEl;
        col.remove();
    }

    const mainContainerId = type === 'component' ? 'newComponentsContainer' : 'newTemplatesContainer';
    if (list.length === 0) {
        document.getElementById(mainContainerId).style.display = 'none';
    }
    checkSaveVisibility();
}

function showSave() {
    document.getElementById('saveBtn').style.display = 'inline-block';
    setDeployDisabled(true);
}

function checkSaveVisibility() {
    if (selectedComponents.length === 0 && selectedTemplates.length === 0) {
        document.getElementById('saveBtn').style.display = 'none';
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
            const msg = document.getElementById('successMessage');
            msg.style.display = 'block';
            setTimeout(() => {
                msg.style.display = 'none';
                window.location.reload();
            }, 1500);
        })
        .catch(err => {
            console.error(err);
            alert('Save failed. Please check server logs and try again.');
            setDeployDisabled(false);

        });
}
//  New — Open Tool Modal

// 🧰 Open Tool Modal and Load Tools
// function openToolModal() {
// const el = document.getElementById('toolModal');
// const projectName = el ? el.getAttribute('data-project') : '';
//       if (!projectName) return;
//     Promise.all([
//         fetch(`/tools/fetchtools/${projectName}`).then(res => res.json()),
//         fetch(`/tools/existingtools/${projectName}`).then(res => res.json())

//     ])
//     .then(([allTools, existingTools]) => {
//         console.log("✅ All Tools:", allTools);
//         console.log("📦 Existing Tools:", existingTools);

//         renderToolList(allTools, existingTools);
//         new bootstrap.Modal(document.getElementById('toolModal')).show();
//     })
//     .catch(err => {
//         console.error("❌ Error loading tools:", err);
//         alert('Unable to load tools. Please try again.');
//     });
// }

// // 🎨 Render Tool List in Modal
// function renderToolList(allTools, existingTools = []) {
//     const container = document.getElementById("toolList");
//     container.innerHTML = "";

//     if (!allTools || allTools.length === 0) {
//         container.innerHTML = `<p class="text-muted">No tools found in the library.</p>`;
//         return;
//     }

//     allTools.forEach(tool => {
//         const isExisting = existingTools.includes(tool);
//         const div = document.createElement("div");
//         div.classList.add("col");

//         div.innerHTML = `
//             <div class="card p-3 shadow-sm border-0 h-100 ${isExisting ? 'bg-light' : ''}">
//                 <div class="form-check">
//                     <input
//                         type="checkbox"
//                         class="form-check-input me-2"
//                         id="tool-${tool}"
//                         value="${tool}"
//                         ${isExisting ? "disabled checked" : ""}
//                     >
//                     <label for="tool-${tool}" class="form-check-label fw-semibold">
//                         ${tool} ${isExisting ? '<span class="text-muted small">(already added)</span>' : ''}
//                     </label>
//                 </div>
//             </div>
//         `;
//         container.appendChild(div);
//     });
// }

// // 🚀 Add Selected Tools
// function addSelectedTools() {
//   const el = document.getElementById('toolModal');
//   const projectName = el ? el.getAttribute('data-project') : '';
//         if (!projectName) return;

//     const selectedTools = Array.from(
//         document.querySelectorAll('#toolList input[type="checkbox"]:checked:not(:disabled)')
//     ).map(cb => cb.value);

//     if (selectedTools.length === 0) {
//         alert("Please select at least one tool.");
//         return;
//     }

//     console.log("🧩 Selected Tools:", selectedTools);

//     fetch(`/tools/add/${projectName}`, {
//         method: 'POST',
//         headers: { 'Content-Type': 'application/json' },
//         body: JSON.stringify(selectedTools)
//     })
//     .then(res => {
//         if (!res.ok) throw new Error("Failed to add tools");
//         return res.text();
//     })
//     .then(() => {
//         alert(`✅ Successfully added ${selectedTools.length} tool(s) to project '${projectName}'`);
//         window.location.reload();
//     })
//     .catch(err => {
//         console.error(err);
//         alert("❌ Failed to add tools. Please check server logs.");
//     });
// }

//  window.addEventListener("DOMContentLoaded", () => {
//         const flash = document.getElementById("flashMessage");
//         if (flash) {
//             setTimeout(() => flash.remove(), 5000); // remove after animation
//         }
//     });
