document.addEventListener('DOMContentLoaded', () => {
    const projectName = document.getElementById('projectName').value;
    const templateList = document.getElementById('templateList');
    const componentList = document.getElementById('componentList');
    const policyList = document.getElementById('policyList');
    const policyForm = document.getElementById('policyForm');
    const styleGroupsDiv = document.getElementById('styleGroups');
    const addGroupBtn = document.getElementById('addGroup');
    const deleteBtn = document.getElementById('deletePolicy');
    let loadedPolicy = null;

    fetch(`/templates/list/${projectName}`)
        .then(r => r.json())
        .then(templates => {
            templates.forEach(t => {
                const li = document.createElement('li');
                li.textContent = t;
                li.className = 'list-group-item list-group-item-action';
                li.addEventListener('click', () => loadComponents(t));
                templateList.appendChild(li);
            });
        });

    function clearForm() {
        policyForm.reset();
        styleGroupsDiv.innerHTML = '';
        deleteBtn.classList.add('d-none');
        loadedPolicy = null;
    }

    function loadComponents(template) {
        document.getElementById('templateName').value = template;
        componentList.innerHTML = '';
        policyList.innerHTML = '';
        clearForm();
        fetch(`/api/policy/${projectName}/${template}/components`)
            .then(r => r.json())
            .then(comps => {
                comps.forEach(c => {
                    const li = document.createElement('li');
                    li.textContent = c;
                    li.className = 'list-group-item list-group-item-action';
                    li.addEventListener('click', () => loadPolicies(c));
                    componentList.appendChild(li);
                });
            });
    }

    function loadPolicies(component) {
        document.getElementById('componentName').value = component;
        policyList.innerHTML = '';
        clearForm();
        fetch(`/api/policy/${projectName}/component/${component}`)
            .then(r => r.json())
            .then(policies => {
                policies.forEach(p => {
                    const li = document.createElement('li');
                    li.textContent = p;
                    li.className = 'list-group-item list-group-item-action';
                    li.addEventListener('click', () => loadPolicyDetail(component, p));
                    policyList.appendChild(li);
                });
            });
    }

    function createStyleRow(style = {}) {
        const row = document.createElement('div');
        row.className = 'style-row input-group mb-1';
        row.innerHTML = `
            <input type="text" class="form-control form-control-sm style-name" placeholder="Style Name" value="${style.name || ''}">
            <input type="text" class="form-control form-control-sm style-class" placeholder="Classes" value="${style.className || ''}">
            <input type="text" class="form-control form-control-sm style-title" placeholder="Title" value="${style.title || ''}">
            <div class="input-group-text">
                <input type="checkbox" class="form-check-input mt-0 style-default" ${style.defaultStyle ? 'checked' : ''}>
            </div>
            <button type="button" class="btn btn-sm btn-outline-danger remove-style">×</button>
        `;
        row.querySelector('.remove-style').addEventListener('click', () => row.remove());
        return row;
    }

    function createGroup(name = '', styles = []) {
        const wrapper = document.createElement('div');
        wrapper.className = 'border p-2 mb-2 style-group';
        const header = document.createElement('div');
        header.className = 'd-flex mb-1';
        header.innerHTML = `
            <input type="text" class="form-control form-control-sm group-name" placeholder="Group Name" value="${name}">
            <button type="button" class="btn btn-sm btn-outline-secondary ms-2 add-style">Add Style</button>
            <button type="button" class="btn btn-sm btn-outline-danger ms-2 remove-group">×</button>
        `;
        wrapper.appendChild(header);
        const list = document.createElement('div');
        list.className = 'style-list';
        wrapper.appendChild(list);
        header.querySelector('.add-style').addEventListener('click', () => list.appendChild(createStyleRow()));
        header.querySelector('.remove-group').addEventListener('click', () => wrapper.remove());
        styles.forEach(st => list.appendChild(createStyleRow(st)));
        return wrapper;
    }

    function loadPolicyDetail(component, policy) {
        document.getElementById('policyName').value = policy;
        clearForm();
        document.getElementById('policyName').value = policy;
        fetch(`/api/policy/${projectName}/component/${component}/${policy}`)
            .then(r => r.json())
            .then(data => {
                if (data) {
                    loadedPolicy = policy;
                    deleteBtn.classList.remove('d-none');
                    document.getElementById('defaultClasses').value = data.styleDefaultClasses || '';
                    data.styleGroups.forEach(g => {
                        styleGroupsDiv.appendChild(createGroup(g.name, g.styles));
                    });
                }
            });
    }

    policyForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const template = document.getElementById('templateName').value;
        const component = document.getElementById('componentName').value;
        const name = document.getElementById('policyName').value;
        const def = document.getElementById('defaultClasses').value;
        const groups = [];
        styleGroupsDiv.querySelectorAll('.style-group').forEach(g => {
            const gName = g.querySelector('.group-name').value;
            const styles = [];
            g.querySelectorAll('.style-row').forEach(r => {
                const sName = r.querySelector('.style-name').value;
                if (!sName) return;
                styles.push({
                    name: sName,
                    className: r.querySelector('.style-class').value,
                    title: r.querySelector('.style-title').value,
                    defaultStyle: r.querySelector('.style-default').checked
                });
            });
            if (gName) {
                groups.push({ name: gName, styles });
            }
        });
        const body = { policyName: name, styleDefaultClasses: def, styleGroups: groups };
        fetch(`/api/policy/${projectName}/${template}/component/${component}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        }).then(() => loadPolicies(component));
    });

    addGroupBtn.addEventListener('click', () => {
        styleGroupsDiv.appendChild(createGroup());
    });

    deleteBtn.addEventListener('click', () => {
        const template = document.getElementById('templateName').value;
        const component = document.getElementById('componentName').value;
        if (loadedPolicy) {
            fetch(`/api/policy/${projectName}/${template}/component/${component}/${loadedPolicy}`, {
                method: 'DELETE'
            }).then(() => { loadPolicies(component); clearForm(); });
        }
    });
});
