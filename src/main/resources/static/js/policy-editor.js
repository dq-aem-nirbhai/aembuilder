document.addEventListener('DOMContentLoaded', () => {
    const project = document.getElementById('projectName').value;
    const templateSelect = document.getElementById('templateSelect');
    const componentSelect = document.getElementById('componentSelect');
    const styleGroupsDiv = document.getElementById('styleGroups');
    const addGroupBtn = document.getElementById('addGroupBtn');
    const saveBtn = document.getElementById('savePolicy');

    function loadTemplates() {
        fetch(`/api/${project}/policy/templates`)
            .then(r => r.json())
            .then(templates => {
                templateSelect.innerHTML = '<option value="" disabled selected>Select template</option>';
                templates.forEach(t => {
                    const opt = document.createElement('option');
                    opt.value = t;
                    opt.textContent = t;
                    templateSelect.appendChild(opt);
                });
            });
    }

    templateSelect.addEventListener('change', () => {
        const template = templateSelect.value;
        fetch(`/api/${project}/policy/${template}/components`)
            .then(r => r.json())
            .then(components => {
                componentSelect.innerHTML = '<option value="" disabled selected>Select component</option>';
                components.forEach(c => {
                    const opt = document.createElement('option');
                    opt.value = c;
                    opt.textContent = c;
                    componentSelect.appendChild(opt);
                });
            });
    });

    componentSelect.addEventListener('change', () => {
        const template = templateSelect.value;
        const component = componentSelect.value;
        fetch(`/api/${project}/policy/${template}/${component}`)
            .then(r => r.json())
            .then(data => {
                document.getElementById('policyName').value = data.name || '';
                document.getElementById('policyTitle').value = data.title || '';
                document.getElementById('policyDescription').value = data.description || '';
                document.getElementById('defaultCss').value = data.defaultCssClass || '';
                styleGroupsDiv.innerHTML = '';
                if (data.styleGroups) {
                    data.styleGroups.forEach(g => addStyleGroup(g));
                }
            });
    });

    addGroupBtn.addEventListener('click', () => addStyleGroup());

    function addStyleGroup(data) {
        const groupDiv = document.createElement('div');
        groupDiv.className = 'card p-3 mb-2 style-group';
        groupDiv.innerHTML = `
            <div class="d-flex justify-content-between">
                <div class="mb-2 flex-grow-1">
                    <label class="form-label">Group Name</label>
                    <input type="text" class="form-control group-name" value="${data ? data.name : ''}">
                </div>
                <div class="ms-3 form-check align-self-end">
                    <input class="form-check-input group-combine" type="checkbox" ${data && data.combine ? 'checked' : ''}>
                    <label class="form-check-label">Combine</label>
                </div>
                <button type="button" class="btn btn-sm btn-danger remove-group ms-2">X</button>
            </div>
            <div class="style-entries"></div>
            <button type="button" class="btn btn-sm btn-secondary add-style">Add Style</button>
        `;
        groupDiv.querySelector('.remove-group').addEventListener('click', () => groupDiv.remove());
        groupDiv.querySelector('.add-style').addEventListener('click', () => addStyleEntry(groupDiv));
        styleGroupsDiv.appendChild(groupDiv);
        if (data && data.styles) {
            data.styles.forEach(s => addStyleEntry(groupDiv, s));
        }
    }

    function addStyleEntry(groupDiv, data) {
        const container = groupDiv.querySelector('.style-entries');
        const entryDiv = document.createElement('div');
        entryDiv.className = 'd-flex mb-2 style-entry';
        entryDiv.innerHTML = `
            <input type="text" class="form-control me-2 style-name" placeholder="Style Name" value="${data ? data.name : ''}">
            <input type="text" class="form-control me-2 style-class" placeholder="CSS Classes" value="${data ? data.cssClass : ''}">
            <button type="button" class="btn btn-sm btn-danger remove-style">X</button>
        `;
        entryDiv.querySelector('.remove-style').addEventListener('click', () => entryDiv.remove());
        container.appendChild(entryDiv);
    }

    function collectPolicy() {
        const policy = {
            name: document.getElementById('policyName').value,
            title: document.getElementById('policyTitle').value,
            description: document.getElementById('policyDescription').value,
            defaultCssClass: document.getElementById('defaultCss').value,
            styleGroups: []
        };
        styleGroupsDiv.querySelectorAll('.style-group').forEach(groupDiv => {
            const group = {
                name: groupDiv.querySelector('.group-name').value,
                combine: groupDiv.querySelector('.group-combine').checked,
                styles: []
            };
            groupDiv.querySelectorAll('.style-entry').forEach(entry => {
                group.styles.push({
                    name: entry.querySelector('.style-name').value,
                    cssClass: entry.querySelector('.style-class').value
                });
            });
            policy.styleGroups.push(group);
        });
        return policy;
    }

    saveBtn.addEventListener('click', () => {
        const template = templateSelect.value;
        const component = componentSelect.value;
        if (!template || !component) {
            alert('Select template and component');
            return;
        }
        const policy = collectPolicy();
        fetch(`/api/${project}/policy/${template}/${component}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(policy)
        }).then(r => r.text()).then(msg => alert(msg));
    });

    loadTemplates();
});
