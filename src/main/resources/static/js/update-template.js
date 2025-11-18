document.addEventListener("DOMContentLoaded", () => {
    const projectnameInput = document.getElementById("projectname");
    if (!projectnameInput) return; // Safety check

    const projectName = projectnameInput.value;
    const templatetypeSelect = document.getElementById("templatetype");
    const selectedTemplateType = templatetypeSelect.getAttribute("data-selected");

    const nameInput = document.getElementById("name");
    const nameError = document.getElementById("name-error");

    // ✅ Restrict spaces in template name field
    if (nameInput) {
        nameInput.addEventListener("input", function () {
            let currentValue = this.value;

            // If user types or pastes spaces → remove them
            if (/\s/.test(currentValue)) {
                this.value = currentValue.replace(/\s+/g, "");
                nameError.innerText = "⚠️ Spaces are not allowed in the template name, you can use - or _ instead of spaces.";
                return;
            } else {
                nameError.innerText = "";
            }
        });
    }

    // ✅ Prevent spaces at submission as a final check
    const templateForm = document.getElementById("templateForm");
    if (templateForm) {
        templateForm.addEventListener("submit", (e) => {
            const nameValue = nameInput.value.trim();
            if (/\s/.test(nameValue)) {
                e.preventDefault();
                nameError.innerText = "Spaces are not allowed in the template name.";
                return false;
            }
        });
    }

    // Fetch available template types
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

        // ✅ Delete button functionality
            const deleteBtn = document.getElementById("delete-template-btn");
            if (deleteBtn) {
                deleteBtn.addEventListener("click", () => {
                    const confirmed = confirm("Are you sure you want to delete this template? This action cannot be undone.");
                    if (confirmed) {
                        const deleteForm = document.getElementById("deleteForm");
                        if (deleteForm) {
                            deleteForm.submit();
                        } else {
                            console.error("Delete form not found in DOM.");
                            alert("Error: Delete form not found. Please reload the page and try again.");
                        }
                    }
                });
            }

});
