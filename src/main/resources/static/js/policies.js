const accordionContainer = document.getElementById('accordionContainer');
const projectName = document.getElementById('projectname').value;
const templateName = document.getElementById('templateName').value;

// Fetch grouped components and build accordion
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

// Build accordion UI dynamically
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

        // Select all option
        const selectAll = document.createElement('label');
        selectAll.innerHTML = `<input type="checkbox" class="select-all" data-group="${group}"/> Select All`;
        content.appendChild(selectAll);

        // Component checkboxes
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

    // Event delegation for select-all and updating paths
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
        updateComponentPath(); // Always update after any change
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
            <input type="text" class="group-name" placeholder="Style Group Name (e.g., backgroundcolor)" required>
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
        <input type="text" placeholder="Style Label (e.g., green)" class="style-label" required>
        <input type="text" placeholder="CSS Class (e.g., .green)" class="style-class" required>
        <button type="button" class="remove-btn" onclick="this.parentElement.remove()">❌</button>
    `;
    stylesDiv.appendChild(row);
}

async function submitPolicy() {
    const form = document.getElementById("policyForm");

    // Collect style groups
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

    const data = {
        projectName: projectName,
        name: form.name.value,
        styleDefaultClasses: form.styleDefaultClasses.value,
        styleDefaultElement: form.styleDefaultElement.value,
        componentPath: componentPath,
        styles: styleGroups
    };

    try {
        const res = await fetch(`/policies/add/${projectName}?templateName=${encodeURIComponent(templateName)}`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(data)
        });



        // Redirect to deploy page on success
        window.location.href = `/${projectName}`;
    } catch (err) {
        alert("Error submitting policy: " + err);
    }
}

// Load components on page load
fetchComponents();
