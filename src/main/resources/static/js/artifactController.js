document.addEventListener("DOMContentLoaded", () => {
    console.log("artifactController.js loaded");
});

// This JS is used by artifact-generator.html to show dynamic fields and submit the form.
const dynamicFields = document.getElementById("dynamicFields");
const artifactTypeEl = document.getElementById("artifactType");
if (artifactTypeEl) {
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
        } else {
            dynamicFields.innerHTML = "";
        }
    });
}

// submit handler: the form is normal HTML POST so no AJAX required in this build
