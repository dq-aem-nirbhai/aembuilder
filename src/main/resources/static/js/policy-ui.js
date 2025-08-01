document.addEventListener('DOMContentLoaded', () => {
    const projectName = document.getElementById('projectName').value;
    const templateList = document.getElementById('templateList');
    const componentList = document.getElementById('componentList');
    const policyList = document.getElementById('policyList');
    const policyForm = document.getElementById('policyForm');

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

    function loadComponents(template) {
        document.getElementById('templateName').value = template;
        componentList.innerHTML = '';
        policyList.innerHTML = '';
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

    function loadPolicyDetail(component, policy) {
        document.getElementById('policyName').value = policy;
        fetch(`/api/policy/${projectName}/component/${component}/${policy}`)
            .then(r => r.json())
            .then(data => {
                if (data) {
                    document.getElementById('defaultClasses').value = data.styleDefaultClasses || '';
                }
            });
    }

    policyForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const template = document.getElementById('templateName').value;
        const component = document.getElementById('componentName').value;
        const name = document.getElementById('policyName').value;
        const def = document.getElementById('defaultClasses').value;
        const body = { policyName: name, styleDefaultClasses: def, styleGroups: [] };
        fetch(`/api/policy/${projectName}/${template}/component/${component}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        }).then(() => loadPolicies(component));
    });
});
