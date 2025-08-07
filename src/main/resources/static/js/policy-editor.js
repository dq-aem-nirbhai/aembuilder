let groupCounter = 0;

function addGroup(data) {
    const container = document.getElementById('styleGroups');
    const index = groupCounter++;
    const group = document.createElement('div');
    group.className = 'border p-3 mb-3';
    group.dataset.index = index;
    group.innerHTML = `
        <div class="d-flex justify-content-between align-items-center mb-2">
            <input type="text" class="form-control me-2" placeholder="Group Name" value="${data ? data.name : ''}" required>
            <div class="form-check">
                <input class="form-check-input" type="checkbox" ${data && data.allowCombination ? 'checked' : ''}>
                <label class="form-check-label">Styles can be combined</label>
            </div>
            <button type="button" class="btn btn-sm btn-danger ms-2" onclick="this.closest('.border').remove()">Remove</button>
        </div>
        <div class="styles"></div>
        <button type="button" class="btn btn-sm btn-outline-secondary mt-2" onclick="addStyle(this.parentElement)">+ Style</button>
    `;
    container.appendChild(group);
    if (data && data.styles) {
        data.styles.forEach(s => addStyle(group, s));
    }
}

function addStyle(groupEl, data) {
    const stylesContainer = groupEl.querySelector('.styles');
    const row = document.createElement('div');
    row.className = 'd-flex mb-2';
    row.innerHTML = `
        <input type="text" class="form-control me-2" placeholder="Style Name" value="${data ? data.name : ''}" required>
        <input type="text" class="form-control me-2" placeholder="CSS Class" value="${data ? data.cssClass : ''}" required>
        <button type="button" class="btn btn-sm btn-outline-danger" onclick="this.parentElement.remove()">X</button>`;
    stylesContainer.appendChild(row);
}

function gatherPolicy() {
    const policy = {
        id: document.getElementById('policyId').value,
        title: document.getElementById('policyTitle').value,
        description: document.getElementById('policyDesc').value,
        defaultCssClass: document.getElementById('defaultCss').value,
        styleGroups: []
    };
    document.querySelectorAll('#styleGroups > div').forEach(groupEl => {
        const name = groupEl.querySelector('input[type="text"]').value;
        const allow = groupEl.querySelector('input[type="checkbox"]').checked;
        const styles = [];
        groupEl.querySelectorAll('.styles > div').forEach(row => {
            const inputs = row.querySelectorAll('input');
            styles.push({ name: inputs[0].value, cssClass: inputs[1].value });
        });
        policy.styleGroups.push({ name: name, allowCombination: allow, styles: styles });
    });
    return policy;
}

document.getElementById('policyForm').addEventListener('submit', function (e) {
    e.preventDefault();
    const policy = gatherPolicy();
    const body = JSON.stringify(policy);
    const project = document.body.dataset.project;
    const template = document.body.dataset.template;
    const component = document.body.dataset.component;

    fetch(`/api/${project}/templates/${template}/component/policy?resource=${encodeURIComponent(component)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: body
    }).then(r => {
        if (r.ok) {
            const isNew = !policy.id;
            showMessage(isNew ? 'Policy created successfully' : 'Policy updated successfully', 'success');

            setTimeout(() => {
                window.location.href = `/${project}/templates/${template}/components`;
            }, 2000);
        } else {
            showMessage('Error saving policy', 'danger');
        }
    }).catch(err => {
        showMessage('Network error', 'danger');
    });
});

document.getElementById('existingPolicy').addEventListener('change', function () {
    const id = this.value;
    if (!id) {
        document.getElementById('policyId').value = '';
        document.getElementById('policyTitle').value = '';
        document.getElementById('policyDesc').value = '';
        document.getElementById('defaultCss').value = '';
        document.getElementById('styleGroups').innerHTML = '';
        return;
    }

    const project = document.body.dataset.project;
    const component = document.body.dataset.component;
    fetch(`/api/${project}/component/policy?resource=${encodeURIComponent(component)}&policyId=${id}`)
        .then(r => r.json()).then(data => {
            document.getElementById('policyId').value = id;
            document.getElementById('policyTitle').value = data.title || '';
            document.getElementById('policyDesc').value = data.description || '';
            document.getElementById('defaultCss').value = data.defaultCssClass || '';
            document.getElementById('styleGroups').innerHTML = '';
            if (data.styleGroups) {
                data.styleGroups.forEach(g => addGroup(g));
            }
        });
});

function createNewPolicy() {
    document.getElementById('existingPolicy').value = '';
    document.getElementById('policyId').value = '';
    document.getElementById('policyTitle').value = '';
    document.getElementById('policyDesc').value = '';
    document.getElementById('defaultCss').value = '';
    document.getElementById('styleGroups').innerHTML = '';
}

// Update option label on title input
document.getElementById('policyTitle').addEventListener('input', function () {
    const select = document.getElementById('existingPolicy');
    const selectedOption = select.options[select.selectedIndex];
    if (selectedOption && selectedOption.value) {
        selectedOption.textContent = this.value || '(Untitled)';
    }
});

// Show success/error message
function showMessage(text, type) {
    const box = document.getElementById('messageBox');
    box.textContent = text;
    box.className = `alert alert-${type}`;
    box.classList.remove('d-none');
}
