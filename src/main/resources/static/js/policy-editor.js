let currentComponent = null;

function openPolicyModal(card) {
    currentComponent = card.getAttribute('data-component');
    const modal = new bootstrap.Modal(document.getElementById('policyModal'));
    loadPolicy();
    modal.show();
}

function loadPolicy() {
    const project = document.body.getAttribute('data-project');
    const template = document.body.getAttribute('data-template');
    fetch(`/${project}/templates/${template}/policies/${currentComponent}`)
        .then(r => r.json())
        .then(data => {
            const form = document.getElementById('policyForm');
            form.title.value = data.title || '';
            form.description.value = data.description || '';
            form.defaultCssClass.value = data.defaultCssClass || '';
            const groupsContainer = document.getElementById('styleGroups');
            groupsContainer.innerHTML = '';
            (data.styleGroups || []).forEach(g => {
                addStyleGroup(g);
            });
        });
}

function addStyleGroup(existing) {
    const container = document.getElementById('styleGroups');
    const idx = container.children.length;
    const div = document.createElement('div');
    div.className = 'border rounded p-2 mb-3';
    div.innerHTML = `
        <div class="mb-2">
            <label class="form-label">Group Name</label>
            <input type="text" class="form-control" name="groups[${idx}].name" required>
        </div>
        <div class="form-check mb-2">
            <input class="form-check-input" type="checkbox" name="groups[${idx}].allowCombination" id="combine${idx}">
            <label class="form-check-label" for="combine${idx}">Styles can be combined</label>
        </div>
        <div class="styles"></div>
        <button type="button" class="btn btn-sm btn-outline-secondary" onclick="addStyle(this)">Add Style</button>
    `;
    container.appendChild(div);

    if (existing) {
        div.querySelector(`input[name='groups[${idx}].name']`).value = existing.name || '';
        div.querySelector(`#combine${idx}`).checked = existing.allowCombination || false;
        (existing.styles || []).forEach(s => addStyle(div.querySelector('.styles'), s));
    }
}

function addStyle(btnOrContainer, existing) {
    const container = btnOrContainer.classList ? btnOrContainer : btnOrContainer.previousElementSibling;
    const idx = container.children.length;
    const div = document.createElement('div');
    div.className = 'mb-2';
    div.innerHTML = `
        <div class="input-group">
          <input type="text" class="form-control" placeholder="Style Name" name="styleName">
          <input type="text" class="form-control" placeholder="CSS Class" name="styleClass">
          <button class="btn btn-outline-danger" type="button" onclick="this.parentNode.parentNode.remove()">×</button>
        </div>`;
    container.appendChild(div);
    if (existing) {
        div.querySelector("input[name='styleName']").value = existing.name || '';
        div.querySelector("input[name='styleClass']").value = existing.cssClass || '';
    }
}

function savePolicy() {
    const project = document.body.getAttribute('data-project');
    const template = document.body.getAttribute('data-template');
    const form = document.getElementById('policyForm');
    const policy = {
        title: form.title.value,
        description: form.description.value,
        defaultCssClass: form.defaultCssClass.value,
        styleGroups: []
    };
    document.querySelectorAll('#styleGroups > div').forEach(groupDiv => {
        const g = {
            name: groupDiv.querySelector('input[name^="groups"]').value,
            allowCombination: groupDiv.querySelector('input[type=checkbox]').checked,
            styles: []
        };
        groupDiv.querySelectorAll('.styles > div').forEach(styleDiv => {
            g.styles.push({
                name: styleDiv.querySelector("input[name='styleName']").value,
                cssClass: styleDiv.querySelector("input[name='styleClass']").value
            });
        });
        policy.styleGroups.push(g);
    });
    fetch(`/${project}/templates/${template}/policies/${currentComponent}`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(policy)
    }).then(() => {
        const modal = bootstrap.Modal.getInstance(document.getElementById('policyModal'));
        modal.hide();
    });
}

document.addEventListener('DOMContentLoaded', () => {
    const body = document.body;
    body.setAttribute('data-project', body.getAttribute('data-project') || document.getElementById('projectName')?.value);
    body.setAttribute('data-template', body.getAttribute('data-template') || document.getElementById('templateName')?.value);
});
