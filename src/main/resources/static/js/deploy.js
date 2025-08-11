    let selectedComponents = [];
    let selectedTemplates = [];

    function getProjectName() {
        return document.getElementById('componentModal').getAttribute('data-project');
    }

    function renderList(containerId, dataList, selectedList, type) {
        const container = document.getElementById(containerId);
        container.innerHTML = '';

        const allItems = [...new Set([...dataList.unique, ...dataList.duplicate, ...selectedList])];

        allItems.forEach(item => {
            const isAlreadyAdded = selectedList.includes(item);
            const isDuplicate = dataList.duplicate.includes(item);

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

            container.innerHTML += `
                <div class="col">
                    <div class="form-check">
                        <input class="form-check-input" type="checkbox" value="${item}" ${isChecked ? 'checked' : ''} ${isDisabled ? 'disabled' : ''}>
                        <label class="form-check-label ${isDisabled ? 'text-muted' : ''}">${item}${labelSuffix}</label>
                    </div>
                </div>`;
        });
    }

    function openComponentModal() {
        const projectName = getProjectName();
        fetch(`/fetch-components/${projectName}`)
            .then(res => res.json())
            .then(data => {
                renderList('componentList', data, selectedComponents, 'component');
                new bootstrap.Modal(document.getElementById('componentModal')).show();
            });
    }

    function openTemplateModal() {
        const projectName = getProjectName();
        fetch(`/fetch-templates/${projectName}`)
            .then(res => res.json())
            .then(data => {
                renderList('templateList', data, selectedTemplates, 'template');
                new bootstrap.Modal(document.getElementById('templateModal')).show();
            });
    }

    function addSelected(type) {
        const listId = type === 'component' ? 'componentList' : 'templateList';
        const selected = Array.from(document.querySelectorAll(`#${listId} input[type=checkbox]:checked:not(:disabled)`))
                              .map(cb => cb.value);
        const container = document.getElementById(type === 'component' ? 'newComponentsList' : 'newTemplatesList');
        const list = type === 'component' ? selectedComponents : selectedTemplates;

        selected.forEach(item => {
            if (!list.includes(item)) list.push(item);
            const div = document.createElement('div');
            div.className = 'col';
            div.innerHTML = `
                <div class="border rounded p-2 bg-light text-center shadow-sm removable-item">
                    ${item}
                    <span class="remove-btn" onclick="removeItem('${item}', '${type}')">&times;</span>
                </div>`;
            container.appendChild(div);
        });

        document.getElementById(type === 'component' ? 'newComponentsContainer' : 'newTemplatesContainer').style.display = 'block';
        showSave();
        bootstrap.Modal.getInstance(document.getElementById(type + 'Modal')).hide();
    }

    function addSelectedComponents() {
        addSelected('component');
    }

    function addSelectedTemplates() {
        addSelected('template');
    }

    function removeItem(name, type) {
        const list = type === 'component' ? selectedComponents : selectedTemplates;
        const idx = list.indexOf(name);
        if (idx !== -1) list.splice(idx, 1);
        const containerId = type === 'component' ? 'newComponentsList' : 'newTemplatesList';
        const container = document.getElementById(containerId);
        Array.from(container.children).forEach(col => {
            if (col.textContent.includes(name)) col.remove();
        });

        const mainContainerId = type === 'component' ? 'newComponentsContainer' : 'newTemplatesContainer';
        if (list.length === 0) {
            document.getElementById(mainContainerId).style.display = 'none';
        }
        checkSaveVisibility();
    }

    function showSave() {
        document.getElementById('saveBtn').style.display = 'inline-block';
        document.getElementById('deployBtn').disabled = true;
    }

    function checkSaveVisibility() {
        if (selectedComponents.length === 0 && selectedTemplates.length === 0) {
            document.getElementById('saveBtn').style.display = 'none';
            document.getElementById('deployBtn').disabled = false;
        }
    }

    function saveAll() {
        const projectName = getProjectName();
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


        Promise.all(promises).then(() => {
            const msg = document.getElementById('successMessage');
            msg.style.display = 'block';
            setTimeout(() => {
                msg.style.display = 'none';
                window.location.reload();
            }, 2000);
        });
    }

    function showDeploySpinner() {
        document.getElementById('deploySpinner').style.display = 'inline-block';
        document.getElementById('deployBtn').disabled = true;
        return true;
    }
div.innerHTML = `
    <div class="border rounded p-2 text-center shadow-sm removable-item new-item">
        ${item}
        <span class="remove-btn" onclick="removeItem('${item}', '${type}')">&times;</span>
    </div>`;
