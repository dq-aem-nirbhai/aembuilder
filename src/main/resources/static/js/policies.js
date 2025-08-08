document.addEventListener("DOMContentLoaded", function () {
    const accordionContainer = document.getElementById('accordionContainer');
    const projectName = document.getElementById('projectname').value;
    const templateName = document.getElementById('templateName').value;

    async function getPolicies() {
        const existingPolicySelect = document.getElementById("existingPolicy");
        if (projectName) {
            fetch(`/get-existing-policies?projectName=${projectName}`)
                .then(response => response.json())
                .then(data => {
                    existingPolicySelect.innerHTML = '<option value="">-- Select Policy --</option>';
                    if (data.length === 0) {
                        const option = document.createElement("option");
                        option.text = "No existing policies found";
                        option.disabled = true;
                        existingPolicySelect.add(option);
                    } else {
                        data.forEach(policy => {
                            const option = document.createElement("option");
                            option.value = policy;
                            option.text = policy;
                            existingPolicySelect.appendChild(option);
                        });
                    }
                })
                .catch(err => console.error("Error fetching policies:", err));
        }

        document.getElementById("newPolicyTitle").addEventListener("input", function () {
            const title = this.value.trim();
            if (!title) return;
            const exists = Array.from(existingPolicySelect.options).some(opt => opt.value === title);
            if (!exists) {
                const newOption = new Option(title, title);
                existingPolicySelect.appendChild(newOption);
                existingPolicySelect.value = title;
            }
        });

        existingPolicySelect.addEventListener("change", async function () {
            const selectedPolicy = this.value;
            if (!selectedPolicy) return;
            await fetchAndFillPolicyDetails(projectName, selectedPolicy);
        });
    }

    async function fetchComponents() {
        try {
            const res = await fetch(`/policies-components/${projectName}`);
            if (!res.ok) throw new Error("Failed to fetch components");
            const data = await res.json();
            buildAccordion(data);
        } catch (err) {
            console.error("Error fetching components:", err);
        }
    }

    function buildAccordion(data) {
        accordionContainer.innerHTML = '';
        Object.keys(data).forEach(group => {
            const item = document.createElement('div');
            item.className = 'accordion-item';

            const header = document.createElement('div');
            header.className = 'accordion-header';
            header.textContent = group;
            header.addEventListener('click', () => item.classList.toggle('active'));

            const content = document.createElement('div');
            content.className = 'accordion-content';

            const selectAll = document.createElement('label');
            selectAll.innerHTML = `<input type="checkbox" class="select-all" data-group="${group}"/> Select All`;
            content.appendChild(selectAll);

            data[group].forEach(comp => {
                const label = document.createElement('label');
                label.innerHTML = `<input type="checkbox" value="${comp.path}"/> ${comp.name}`;
                content.appendChild(document.createElement('br'));
                content.appendChild(label);
            });

            item.appendChild(header);
            item.appendChild(content);
            accordionContainer.appendChild(item);
        });

        accordionContainer.addEventListener('change', e => {
            if (e.target.classList.contains('select-all')) {
                const group = e.target.dataset.group;
                const checkboxes = accordionContainer.querySelectorAll(
                    '.accordion-content input[type=checkbox]:not(.select-all)'
                );
                checkboxes.forEach(cb => {
                    if (cb.closest('.accordion-item').querySelector('.accordion-header').textContent === group) {
                        cb.checked = e.target.checked;
                    }
                });
            }
            updateComponentPath();
        });
    }

    function updateComponentPath() {
        const selected = Array.from(
            document.querySelectorAll('.accordion-content input[type="checkbox"]:checked:not(.select-all)')
        ).map(cb => cb.value);
        document.getElementById('componentPathOutput').textContent = `[${selected.join(',')}]`;
    }

    function addStyleGroup() {
        const container = document.getElementById('styleGroups');
        const groupDiv = document.createElement('div');
        groupDiv.className = 'style-group';
        groupDiv.innerHTML = `
            <div style="display: flex; justify-content: space-between; align-items: center;">
                <input type="text" class="group-name" placeholder="Style Group Name" required>
                <button type="button" class="remove-btn" onclick="this.closest('.style-group').remove()">❌ Remove Group</button>
            </div>
            <button type="button" class="add-btn" onclick="addStyleRow(this)">+ Add Style</button>
            <div class="styles"></div>
        `;
        container.appendChild(groupDiv);
    }

    function addStyleRow(btn) {
        const stylesDiv = btn.parentElement.querySelector('.styles');
        const row = document.createElement('div');
        row.className = 'style-row';
        row.innerHTML = `
            <input type="text" placeholder="Style Label" class="style-label" required>
            <input type="text" placeholder="CSS Class" class="style-class" required>
            <button type="button" class="remove-btn" onclick="this.parentElement.remove()">❌</button>
        `;
        stylesDiv.appendChild(row);
    }

    async function fetchAndFillPolicyDetails(projectName, policyTitle) {
        const response = await fetch(`/get-policy-details?projectName=${projectName}&policyTitle=${encodeURIComponent(policyTitle)}`);
        if (!response.ok) return;

        const data = await response.json();
console.log(data);
        document.getElementById('newPolicyTitle').value = data.name || '';
        document.getElementById('componentPathOutput').textContent = data.componentPath || '';
        document.getElementById('styleDefaultClasses').value = data.styleDefaultClasses || '';
        document.getElementById('styleDefaultElement').value = data.styleDefaultElement || '';

        // Select checkboxes based on component path
        const paths = data.componentPath ? data.componentPath.replace(/[\[\]\s]/g, '').split(',') : [];
        document.querySelectorAll('.accordion-content input[type="checkbox"]').forEach(cb => {
            cb.checked = paths.includes(cb.value);
        });

        updateComponentPath();

        // Rebuild style groups
        const container = document.getElementById('styleGroups');
        container.innerHTML = '';
        if (data.styles) {
            Object.entries(data.styles).forEach(([groupName, styles]) => {
                const groupDiv = document.createElement('div');
                groupDiv.className = 'style-group';
                groupDiv.innerHTML = `
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                        <input type="text" class="group-name" value="${groupName}" required>
                        <button type="button" class="remove-btn" onclick="this.closest('.style-group').remove()">❌ Remove Group</button>
                    </div>
                    <button type="button" class="add-btn" onclick="addStyleRow(this)">+ Add Style</button>
                    <div class="styles"></div>
                `;
                const stylesDiv = groupDiv.querySelector('.styles');
                Object.entries(styles).forEach(([label, cssClass]) => {
                    const row = document.createElement('div');
                    row.className = 'style-row';
                    row.innerHTML = `
                        <input type="text" value="${label}" class="style-label" required>
                        <input type="text" value="${cssClass}" class="style-class" required>
                        <button type="button" class="remove-btn" onclick="this.parentElement.remove()">❌</button>
                    `;
                    stylesDiv.appendChild(row);
                });
                container.appendChild(groupDiv);
            });
        }
    }

    async function submitPolicy() {
        const form = document.getElementById("policyForm");

        const styleGroups = {};
        document.querySelectorAll('.style-group').forEach(group => {
            const groupName = group.querySelector('.group-name').value.trim();
            if (!groupName) return;
            const styles = {};
            group.querySelectorAll('.style-row').forEach(row => {
                const label = row.querySelector('.style-label').value.trim();
                const cls = row.querySelector('.style-class').value.trim();
                if (label && cls) styles[label] = cls;
            });
            styleGroups[groupName] = styles;
        });

        const componentPath = document.getElementById('componentPathOutput').textContent;
        const newPolicyTitleInput = document.getElementById("newPolicyTitle").value;

        const data = {
            projectName,
            name: newPolicyTitleInput,
            styleDefaultClasses: form.styleDefaultClasses.value,
            styleDefaultElement: form.styleDefaultElement.value,
            componentPath,
            styles: styleGroups
        };

        try {
            const res = await fetch(`/policies/add/${projectName}?templateName=${encodeURIComponent(templateName)}`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(data)
            });
            window.location.href = `/${projectName}`;
        } catch (err) {
            alert("Error submitting policy: " + err);
        }
    }

    // Expose functions globally if needed
    window.addStyleGroup = addStyleGroup;
    window.addStyleRow = addStyleRow;
    window.submitPolicy = submitPolicy;

    // Init
    getPolicies();
    fetchComponents();
});
