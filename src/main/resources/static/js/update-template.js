document.addEventListener("DOMContentLoaded", () => {
    const projectnameInput = document.getElementById("projectname");
    if (!projectnameInput) return; // Safety check

    const projectName = projectnameInput.value;
    const templatetypeSelect = document.getElementById("templatetype");
    const selectedTemplateType = templatetypeSelect.getAttribute("data-selected");

    fetch(`/template-types/${projectName}`)
        .then(response => {
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            return response.json();
        })
        .then(data => {
            data.forEach(type => {
                const option = document.createElement("option");
                option.value = type;
                option.textContent = type;
                if (type === selectedTemplateType) {
                    option.selected = true;
                }
                templatetypeSelect.appendChild(option);
            });
        })
        .catch(error => console.error("Error loading template types:", error));
});
