
// document.addEventListener("DOMContentLoaded", function () {
//     let existingTemplates = [];

//     const projectnameInput = document.getElementById("projectname");
//     const nameInput = document.getElementById("name");
//     const titleInput = document.getElementById("title");
//     const descriptionInput = document.getElementById("description");
//     const statusInput = document.getElementById("status");
//     const templatetypeSelect = document.getElementById("templatetype");
//     const nameError = document.getElementById("name-error");
//     const responseEl = document.getElementById("response");
//     const templateForm = document.getElementById("templateForm");
//     const spinnerOverlay = document.getElementById("spinner-overlay");

//     if (!projectnameInput || !nameInput || !templateForm || !templatetypeSelect) {
//         console.error("One or more required elements are missing in the HTML.");
//         return;
//     }

//     const projectName = projectnameInput.value;

//     // Disable all form fields initially except template type
//     const formFields = document.querySelectorAll('#templateForm input, #templateForm select, #templateForm button');
//     formFields.forEach(field => {
//         if (field !== templatetypeSelect) {
//             field.disabled = true;
//         }
//     });

//     // Enable fields when a template type is selected
//     templatetypeSelect.addEventListener("change", () => {
//         const enable = templatetypeSelect.value !== "";
//         formFields.forEach(field => {
//             if (field !== templatetypeSelect) field.disabled = !enable;
//         });
//     });

//     // Fetch template types
//     fetch(`/template-types/${projectName}`)
//         .then(response => {
//             if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
//             return response.json();
//         })
//         .then(data => {
//             data.forEach(type => {
//                 const option = document.createElement("option");
//                 option.value = type;
//                 option.textContent = type;
//                 templatetypeSelect.appendChild(option);
//             });
//         })
//         .catch(error => console.error("Error loading template types:", error));

//     // Load existing template names when name field is focused
//     nameInput.addEventListener("focus", function () {
//         if (existingTemplates.length === 0) {
//             fetch(`/templates/list/${projectName}`)
//                 .then(response => response.json())
//                 .then(data => {
//                     existingTemplates = data.map(name => name.toLowerCase());
//                 })
//                 .catch(error => console.error("Error fetching existing templates:", error));
//         }
//     });

//     // Check for duplicate name while typing
//     nameInput.addEventListener("input", function () {
//         const inputName = this.value.trim().toLowerCase();
//         nameError.innerText = existingTemplates.includes(inputName) ? "Template already exists." : "";
//     });

//     // Handle form submission
//     templateForm.addEventListener("submit", function (event) {
//         event.preventDefault();

//         const name = nameInput.value.trim();
//         const title = titleInput.value.trim();
//         const description = descriptionInput.value.trim();
//         const status = statusInput.value;
//         const templateType = templatetypeSelect.value;

//         if (existingTemplates.includes(name.toLowerCase())) {
//             nameError.innerText = "Template already exists.";
//             return;
//         }

//         const data = { name, title, description, status, templateType };

//         // Show spinner
//         spinnerOverlay.classList.remove("d-none");
//         const spinnerStart = Date.now();

//         fetch(`/create-template/${projectName}`, {
//             method: "POST",
//             headers: { "Content-Type": "application/json" },
//             body: JSON.stringify(data)
//         })
//             .then(response => response.text())
//             .then(result => {
//                 responseEl.style.color = "green";
//                 responseEl.innerText = result;
//                 templateForm.reset();
//                 nameError.innerText = "";
//                 existingTemplates.push(name.toLowerCase());

//                 setTimeout(() => {
//                     window.location.href = `/view/${projectName}`;
//                 }, 1000);

//                 // Reset field disable state
//                 formFields.forEach(field => {
//                     if (field !== templatetypeSelect) field.disabled = true;
//                 });
//             })
//             .catch(error => {
//                 responseEl.style.color = "red";
//                 responseEl.innerText = "Error: " + error;
//             })
//             .finally(() => {
//                 // Ensure spinner stays for at least 5 seconds
//                 const elapsed = Date.now() - spinnerStart;
//                 const remaining = Math.max(0, 5000 - elapsed);

//                 setTimeout(() => {
//                     spinnerOverlay.classList.add("d-none");
//                 }, remaining);
//             });
//     });
// });
