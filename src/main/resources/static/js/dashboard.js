document.addEventListener("DOMContentLoaded", () => {
  const importForm = document.getElementById("importForm");
  const uploadBtn = document.getElementById("uploadBtn");
  const uploadSpinner = document.getElementById("uploadSpinner");
  const zipFile = document.getElementById("zipFile");
  const feedback = document.getElementById("importFeedback");

  // Auto-hide any bootstrap alerts after 5s
  setTimeout(() => {
    document.querySelectorAll(".alert").forEach(alert => new bootstrap.Alert(alert).close());
  }, 5000);

  // Validate ZIP when file is selected
  zipFile.addEventListener("change", () => {
    const file = zipFile.files[0];
    feedback.innerHTML = "";
    uploadBtn.disabled = true;

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
        }
      })
      .catch(() => {
        feedback.innerHTML = `<div class="text-danger">Validation failed. Please try again.</div>`;
        uploadBtn.disabled = true;
      });
  });

  // Handle form submission with AJAX
  importForm.addEventListener("submit", (e) => {
    e.preventDefault(); // prevent default POST

    const file = zipFile.files[0];
    if (!file) return;

    const formData = new FormData();
    formData.append("file", file);

    // Show spinner and disable button
    uploadSpinner.style.display = "inline-block";
    uploadBtn.disabled = true;
    feedback.innerHTML = `<div class="text-info">Uploading...</div>`;

    fetch("/import", { method: "POST", body: formData })
      .then(res => res.json())
      .then(data => {
        uploadSpinner.style.display = "none";

        if (data.success) {
          feedback.innerHTML = `<div class="text-success">✅ ${data.message}</div>`;
          zipFile.value = ""; // reset file input
          uploadBtn.disabled = true; // disable until new file selected

          // Redirect to dashboard after 1s
          setTimeout(() => {
            window.location.href = "/dashboard";
          }, 1000);
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
  });
});
