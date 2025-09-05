document.addEventListener("DOMContentLoaded", () => {
    const projectnameInput = document.getElementById("projectName");
    if (!projectnameInput) return;

    const projectName = projectnameInput.value;
    const templatetypeSelect = document.getElementById("templateType");
    const selectedTemplateType = templatetypeSelect.getAttribute("data-selected");

    fetch(`/template-types/${projectName}`)
        .then(response => response.json())
        .then(data => {
            data.forEach(type => {
                const option = document.createElement("option");
                option.value = type;
                option.textContent = type;
                if (selectedTemplateType && type.trim() === selectedTemplateType.trim()) {
                    option.selected = true;
                }
                templatetypeSelect.appendChild(option);
            });
        })
        .catch(error => console.error("Error loading template types:", error));
});
