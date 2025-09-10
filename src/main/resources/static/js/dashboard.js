document.addEventListener("DOMContentLoaded", () => {
  const importForm = document.getElementById("importForm");
  const uploadBtn = document.getElementById("uploadBtn");
  const uploadSpinner = document.getElementById("uploadSpinner");
  const zipFile = document.getElementById("zipFile");
  const repoUrl = document.getElementById("repoUrl");
  const feedback = document.getElementById("importFeedback");

  function resetState() {
    uploadBtn.disabled = true;
    feedback.innerHTML = "";
    uploadBtn.innerHTML = `<i class="bi bi-cloud-arrow-up me-1"></i> Upload`;
  }

  // Handle File Upload Selection
  zipFile.addEventListener("change", () => {
    const file = zipFile.files[0];
    feedback.innerHTML = "";
    uploadBtn.disabled = true;
    repoUrl.value = ""; // clear URL if file chosen

    if (!file) return;

    const formData = new FormData();
    formData.append("file", file);

    fetch("/validateImport", { method: "POST", body: formData })
      .then(res => res.json())
      .then(data => {
        if (!data.valid) {
          feedback.innerHTML = `<div class="text-danger">${data.error}</div>`;
          uploadBtn.disabled = true;
        } else {
          feedback.innerHTML = `<div class="text-success">✅ Project is valid and ready to import.</div>`;
          uploadBtn.disabled = false;
          uploadBtn.innerHTML = `<i class="bi bi-cloud-arrow-up me-1"></i> Upload`;
        }
      })
      .catch(() => {
        feedback.innerHTML = `<div class="text-danger">Validation failed. Please try again.</div>`;
        uploadBtn.disabled = true;
      });
  });

  // Handle Repo URL Input
  repoUrl.addEventListener("input", () => {
    const url = repoUrl.value.trim();
    feedback.innerHTML = "";
    zipFile.value = ""; // clear file if URL entered

    if (url === "") {
      resetState();
      return;
    }

    uploadBtn.disabled = false;
    uploadBtn.innerHTML = `<i class="bi bi-git me-1"></i> Clone`;
  });

  // Handle Submit
  importForm.addEventListener("submit", (e) => {
    e.preventDefault();

    const file = zipFile.files[0];
    const url = repoUrl.value.trim();
    if (!file && !url) return;

    uploadSpinner.style.display = "inline-block";
    uploadBtn.disabled = true;
    feedback.innerHTML = `<div class="text-info">Processing...</div>`;

    if (file) {
      // Upload file
      const formData = new FormData();
      formData.append("file", file);

      fetch("/import", { method: "POST", body: formData })
        .then(res => res.json())
        .then(data => {
          uploadSpinner.style.display = "none";
          if (data.success) {
            feedback.innerHTML = `<div class="text-success">✅ ${data.message}</div>`;
            zipFile.value = "";
            uploadBtn.disabled = true;
            setTimeout(() => window.location.href = "/dashboard", 1000);
          } else {
            feedback.innerHTML = `<div class="text-danger">❌ ${data.error}</div>`;
            uploadBtn.disabled = false;
          }
        })
        .catch(err => {
          uploadSpinner.style.display = "none";
          uploadBtn.disabled = false;
          feedback.innerHTML = `<div class="text-danger">Upload failed. Please try again.</div>`;
          console.error(err);
        });
    } else if (url) {
      // Clone repo
      fetch("/clone", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ repoUrl: url })
      })
        .then(res => res.json())
        .then(data => {
          uploadSpinner.style.display = "none";
          if (data.success) {
            feedback.innerHTML = `<div class="text-success">✅ ${data.message}</div>`;
            repoUrl.value = "";
            uploadBtn.disabled = true;
            setTimeout(() => window.location.href = "/dashboard", 1000);
          } else {
            feedback.innerHTML = `<div class="text-danger">❌ ${data.error}</div>`;
            uploadBtn.disabled = false;
          }
        })
        .catch(err => {
          uploadSpinner.style.display = "none";
          uploadBtn.disabled = false;
          feedback.innerHTML = `<div class="text-danger">Clone failed. Please try again.</div>`;
          console.error(err);
        });
    }
  });
});
