document.addEventListener("DOMContentLoaded", () => {
    console.log("Combined Artifact Library + Generator loaded");

    const showFormBtn = document.getElementById("showFormBtn");
    const cancelBtn = document.getElementById("cancelBtn");
    const artifactFormSection = document.getElementById("artifactFormSection");
    const artifactListSection = document.getElementById("artifactListSection");
    const form = document.getElementById("artifactForm");
    const messageDiv = document.getElementById("message");
    const artifactTypeEl = document.getElementById("artifactType");
    const dynamicFields = document.getElementById("dynamicFields");

    // ✅ Create new message area for list section
    const listMessageDiv = document.createElement("div");
    listMessageDiv.id = "listMessage";
    artifactListSection.prepend(listMessageDiv);

    // ✅ Toggle visibility
    showFormBtn.addEventListener("click", () => {
        artifactListSection.style.display = "none";
        artifactFormSection.style.display = "block";
        listMessageDiv.innerHTML = ""; // clear old messages
    });

    cancelBtn.addEventListener("click", () => {
        artifactFormSection.style.display = "none";
        artifactListSection.style.display = "block";
    });

    // ✅ Handle dynamic fields
    artifactTypeEl.addEventListener("change", (e) => {
        const type = e.target.value;
        dynamicFields.innerHTML = "";

        if (type === "servlet") {
            dynamicFields.innerHTML = `
                <div class="mb-3">
                    <label class="form-label">HTTP Method</label>
                    <select name="method" class="form-select">
                        <option value="GET">GET</option>
                        <option value="POST">POST</option>
                    </select>
                </div>
                <div class="mb-3">
                    <label class="form-label">Path</label>
                    <input type="text" name="path" class="form-control" placeholder="/bin/example">
                </div>`;
        } else if (type === "scheduler") {
            dynamicFields.innerHTML = `
                <div class="mb-3">
                    <label class="form-label">Cron Expression</label>
                    <input type="text" name="cron" class="form-control" value="0 0 * * * ?" placeholder="Cron expression">
                </div>`;
        } else if (type === "listener") {
            dynamicFields.innerHTML = `
                <div class="mb-3">
                    <label class="form-label">Event Type</label>
                    <input type="text" name="eventType" class="form-control" value="resource" placeholder="Event type">
                </div>`;
        } else if (type === "service") {
            dynamicFields.innerHTML = `
                <div class="mb-3">
                    <label class="form-label">Interface Type</label>
                    <input type="text" name="interfaceType" class="form-control" value="Component">
                </div>`;
        }
    });

    // ✅ Submit form (AJAX)
    form.addEventListener("submit", async (e) => {
        e.preventDefault();

        const formData = new FormData(form);
        const url = form.getAttribute("action");

        try {
            const response = await fetch(url, { method: "POST", body: formData });
            const result = await response.text();

            // ✅ show message in list section instead of form
            listMessageDiv.innerHTML = `<div class="alert alert-success">✅ Artifact generated successfully!</div>`;

            artifactFormSection.style.display = "none";
            artifactListSection.style.display = "block";

            form.reset(); // clear form for next time
            dynamicFields.innerHTML = "";
        } catch (error) {
            console.error("Error:", error);
            listMessageDiv.innerHTML = `<div class="alert alert-danger">❌ Error while generating artifact!</div>`;
            artifactFormSection.style.display = "none";
            artifactListSection.style.display = "block";
        }
    });
});
