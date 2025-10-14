document.addEventListener("DOMContentLoaded", () => {
  const importForm = document.getElementById("importForm");
  const uploadBtn = document.getElementById("uploadBtn");
  const uploadSpinner = document.getElementById("uploadSpinner");
  const zipFile = document.getElementById("zipFile");
  const repoUrl = document.getElementById("repoUrl");
  const feedback = document.getElementById("importFeedback");
  const helpTourBtn = document.getElementById("helpTourBtn");

  /** Reset form to default state */
  function resetState() {
    uploadBtn.disabled = true;
    feedback.innerHTML = "";
    uploadSpinner.style.display = "none";
    zipFile.value = "";
    repoUrl.value = "";
    uploadBtn.innerHTML = `<i class="bi bi-cloud-arrow-up me-1"></i> Upload`;
  }

  /** Flash message auto-fade */
  setTimeout(() => {
    const flash = document.getElementById("flashMessage");
    if (flash) flash.classList.add("fade");
  }, 3000);

  /** -------------------------------
   * Intro.js Quick Tour
   * ------------------------------- */
  const tourShown = localStorage.getItem("aemDashboardTourShown");
  const hasProjects = document.querySelectorAll(".project-card").length > 0;

  const steps = [
    { element: document.querySelector("[data-step='1']"), intro: "Click here to create a new AEM project." },
    { element: document.querySelector("[data-step='2']"), intro: "You can also import existing projects from ZIP or Git." },
  ];

  if (hasProjects) {
    steps.push(
      { element: document.querySelector("[data-step='4']"), intro: "Each project provides quick actions here — let's explore them!" },
      { element: document.querySelector("[data-step='5']"), intro: "Click to view this project's full structure and details." },
      { element: document.querySelector("[data-step='6']"), intro: "Open the folder where this project is stored on your system." },
      { element: document.querySelector("[data-step='7']"), intro: "Download the project as a ZIP file for backup or sharing." }
    );
  }

  function startTour() {
    introJs().setOptions({
      steps: steps.filter(s => s.element),
      showProgress: true,
      showBullets: false,
      exitOnOverlayClick: false,
      nextLabel: "Next →",
      prevLabel: "← Back",
      doneLabel: "Got it!"
    }).start();
  }

  if (!tourShown && steps.some(s => s.element)) {
    setTimeout(() => {
      startTour();
      localStorage.setItem("aemDashboardTourShown", "true");
    }, 1000);
  }

  if (helpTourBtn) {
    helpTourBtn.addEventListener("click", startTour);
  }

  /** -------------------------------
   * ZIP File Validation
   * ------------------------------- */
  zipFile.addEventListener("change", () => {
    const file = zipFile.files[0];
    feedback.innerHTML = "";
    uploadBtn.disabled = true;
    repoUrl.value = "";

    if (!file) return;

    const formData = new FormData();
    formData.append("file", file);

    fetch("/validateImport", { method: "POST", body: formData })
      .then(res => res.ok ? res.json() : Promise.reject("Invalid response"))
      .then(data => {
        if (data.valid) {
          feedback.innerHTML = `<div class="text-success">✅ Project is valid and ready to import.</div>`;
          uploadBtn.disabled = false;
        } else {
          feedback.innerHTML = `<div class="text-danger">❌ ${data.error}</div>`;
        }
      })
      .catch(() => {
        feedback.innerHTML = `<div class="text-danger">❌ Validation failed. Please try again.</div>`;
      });
  });

  /** -------------------------------
   * Repo URL Input
   * ------------------------------- */
  repoUrl.addEventListener("input", () => {
    const url = repoUrl.value.trim();
    feedback.innerHTML = "";
    zipFile.value = "";
    if (url === "") {
      resetState();
    } else {
      uploadBtn.disabled = false;
      uploadBtn.innerHTML = `<i class="bi bi-git me-1"></i> Clone`;
    }
  });

  /** -------------------------------
   * Import Form Submit
   * ------------------------------- */
  importForm.addEventListener("submit", e => {
    e.preventDefault();
    const file = zipFile.files[0];
    const url = repoUrl.value.trim();
    if (!file && !url) return;

    uploadSpinner.style.display = "inline-block";
    uploadBtn.disabled = true;
    feedback.innerHTML = `<div class="text-info">Processing...</div>`;

    if (file) {
      const formData = new FormData();
      formData.append("file", file);

      fetch("/import", { method: "POST", body: formData })
        .then(res => res.ok ? res.json() : Promise.reject("Invalid response"))
        .then(data => {
          uploadSpinner.style.display = "none";
          if (data.success) {
            feedback.innerHTML = `<div class="text-success">✅ ${data.message}</div>`;
            setTimeout(() => (window.location.href = "/dashboard"), 1500);
          } else {
            feedback.innerHTML = `<div class="text-danger">❌ ${data.error}</div>`;
            uploadBtn.disabled = false;
          }
        })
        .catch(err => {
          uploadSpinner.style.display = "none";
          feedback.innerHTML = `<div class="text-danger">❌ Upload failed. ${err}</div>`;
          uploadBtn.disabled = false;
        });
    } else {
      fetch("/clone", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ repoUrl: url })
      })
        .then(res => res.ok ? res.json() : Promise.reject("Invalid response"))
        .then(data => {
          uploadSpinner.style.display = "none";
          if (data.success) {
            feedback.innerHTML = `<div class="text-success">✅ ${data.message}</div>`;
            setTimeout(() => (window.location.href = "/dashboard"), 1500);
          } else {
            feedback.innerHTML = `<div class="text-danger">❌ ${data.error}</div>`;
            uploadBtn.disabled = false;
          }
        })
        .catch(err => {
          uploadSpinner.style.display = "none";
          feedback.innerHTML = `<div class="text-danger">❌ Clone failed. ${err}</div>`;
          uploadBtn.disabled = false;
        });
    }
  });
});
