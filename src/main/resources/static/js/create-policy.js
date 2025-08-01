document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('policyForm');
    form.addEventListener('submit', (e) => {
        e.preventDefault();
        const project = document.getElementById('projectName').value;
        const template = document.getElementById('templateName').value;
        const data = {
            policyName: document.getElementById('policyName').value.trim(),
            componentName: document.getElementById('componentName').value,
            templateName: template,
            styleClassName: document.getElementById('styleClassName').value.trim(),
            styleName: document.getElementById('styleName').value.trim(),
            group: document.getElementById('group').value.trim()
        };
        fetch(`/policy/create/${project}/${template}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        }).then(res => {
            if(res.ok){
                window.location.href = `/${project}`;
            } else {
                alert('Failed to create policy');
            }
        }).catch(() => alert('Failed to create policy'));
    });
});
