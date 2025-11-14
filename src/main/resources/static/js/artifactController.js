document.addEventListener("DOMContentLoaded", () => {
    console.log("Combined Artifact Library + Generator loaded");

    const showFormBtn = document.getElementById("showFormBtn");
    const cancelBtn = document.getElementById("cancelBtn");
    const artifactFormSection = document.getElementById("artifactFormSection");
    const artifactListSection = document.getElementById("artifactListSection");
    const form = document.getElementById("artifactForm");
    const artifactTypeEl = document.getElementById("artifactType");
    const dynamicFields = document.getElementById("dynamicFields");

    // Create message area
    let listMessageDiv = document.getElementById("listMessage");
    if (!listMessageDiv) {
        listMessageDiv = document.createElement("div");
        listMessageDiv.id = "listMessage";
        artifactListSection.prepend(listMessageDiv);
    }

    // Toggle form
    if (showFormBtn) {
        showFormBtn.addEventListener("click", () => {
            artifactListSection.style.display = "none";
            artifactFormSection.style.display = "block";
            listMessageDiv.innerHTML = "";
        });
    }

    // Cancel form
    if (cancelBtn) {
        cancelBtn.addEventListener("click", () => {
            artifactFormSection.style.display = "none";
            artifactListSection.style.display = "block";
            dynamicFields.innerHTML = "";
            form.reset();
        });
    }

    // Dynamic fields
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
            }
        });
    }

    // Submit artifact form
    if (form) {
        form.addEventListener("submit", async (e) => {
            e.preventDefault();
            const formData = new FormData(form);
            const url = form.getAttribute("action");

            try {
                const response = await fetch(url, { method: "POST", body: formData });
                const text = await response.text();

                if (!response.ok) {
                    listMessageDiv.innerHTML = `<div class="alert alert-danger">❌ ${text || 'Error while generating artifact'}</div>`;
                } else {
                    listMessageDiv.innerHTML = `<div class="alert alert-success">✅ Artifact generated successfully!</div>`;
                    artifactFormSection.style.display = "none";
                    artifactListSection.style.display = "block";
                    form.reset();
                    dynamicFields.innerHTML = "";
                }
            } catch (error) {
                console.error("Error:", error);
                listMessageDiv.innerHTML = `<div class="alert alert-danger">❌ Error while generating artifact!</div>`;
                artifactFormSection.style.display = "none";
                artifactListSection.style.display = "block";
            }
        });
    }
});

// ---------- Inline Tool Section ----------
// ------------------ TOOLS SECTION HANDLER ------------------

function openToolSection() {
    const section = document.getElementById("toolListSection");
    const inlineToolList = document.getElementById("inlineToolList");
    const el = document.getElementById("toolModal");
    const projectName = el ? el.getAttribute("data-project") : "";

    if (!projectName) {
        console.error("❌ Project name missing — cannot load tools.");
        return;
    }

    // Toggle section
    if (section.style.display === "block") {
        section.style.display = "none";
        inlineToolList.innerHTML = "";
        return;
    }

    section.style.display = "block";
    inlineToolList.innerHTML = `<p class="text-muted">Loading tools...</p>`;

    // Fetch both all tools and existing tools for project
    Promise.all([
        fetch(`/tools/fetchtools/${projectName}`).then(res => res.json()),
        fetch(`/tools/existingtools/${projectName}`).then(res => res.json())
    ])
    .then(([allTools, existingTools]) => {
        console.log("📦 All tools:", allTools);
        console.log("🧩 Existing tools:", existingTools);
        renderToolListInline(allTools, existingTools);
    })
    .catch(err => {
        console.error("❌ Error loading tools:", err);
        inlineToolList.innerHTML = `<p class="text-danger">Failed to load tools. Please try again later.</p>`;
    });
}

// Render list of tools in inline section
function renderToolListInline(allTools, existingTools = []) {
    const container = document.getElementById("inlineToolList");
    if (!container) return;

    container.innerHTML = "";

    if (!allTools || allTools.length === 0) {
        container.innerHTML = `<p class="text-muted">No tools available in the library.</p>`;
        return;
    }

    allTools.forEach(tool => {
        const isExisting = existingTools.includes(tool);
        const div = document.createElement("div");
        div.classList.add("col", "mb-2");

        div.innerHTML = `
            <div class="card p-3 shadow-sm border-0 h-100 ${isExisting ? 'bg-light' : ''}">
                <div class="form-check">
                    <input type="checkbox" class="form-check-input me-2"
                        id="inline-tool-${escapeId(tool)}"
                        value="${escapeHtml(tool)}"
                        ${isExisting ? "disabled checked" : ""}>
                    <label for="inline-tool-${escapeId(tool)}" class="form-check-label fw-semibold">
                        ${escapeHtml(tool)} ${isExisting ? '<span class="text-muted small">(already added)</span>' : ''}
                    </label>
                </div>
            </div>
        `;
        container.appendChild(div);
    });
}

// Add selected tools (inline version)
function addSelectedTools() {
    const el = document.getElementById('toolModal');
    const projectName = el ? el.getAttribute('data-project') : '';
    if (!projectName) {
        alert("Project name missing — cannot add tools.");
        return;
    }

    const selectedTools = Array.from(
        document.querySelectorAll('#inlineToolList input[type="checkbox"]:checked:not(:disabled)')
    ).map(cb => cb.value);

    if (selectedTools.length === 0) {
        alert("Please select at least one new tool to add.");
        return;
    }

    console.log("🧩 Selected Tools to Add:", selectedTools);

    fetch(`/tools/add-tool/${projectName}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(selectedTools)
    })
    .then(res => {
        if (!res.ok) throw new Error("Failed to add tools");
        return res.text();
    })
    .then(response => {
        if (response === "OK") {
            alert(`✅ Successfully added ${selectedTools.length} tool(s) to project '${projectName}'`);
            window.location.reload();
        } else {
            throw new Error("Server returned error response");
        }
    })
    .catch(err => {
        console.error(err);
        alert("❌ Failed to add tools. Please check server logs.");
    });
}

// Cancel button handler
function cancelToolSelection() {
    const section = document.getElementById('toolListSection');
    const list = document.getElementById('inlineToolList');
    section.style.display = 'none';
    list.innerHTML = '';
}

// ------------------ KEEP EXISTING MODAL LOGIC ------------------
function openToolModal() { /* unchanged */ }
function renderToolList() { /* unchanged */ }

// ------------------ HELPERS ------------------
function escapeId(str) {
    return String(str).replace(/[^a-z0-9\-_]/gi, '-');
}

function escapeHtml(str) {
    return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

// Flash message auto remove
window.addEventListener("DOMContentLoaded", () => {
    const flash = document.getElementById("flashMessage");
    if (flash) setTimeout(() => flash.remove(), 5000);
});
