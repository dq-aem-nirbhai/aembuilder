document.addEventListener("DOMContentLoaded", () => {
  const importForm = document.getElementById("importForm");
  const uploadBtn = document.getElementById("uploadBtn");
  const uploadSpinner = document.getElementById("uploadSpinner");
  const zipFile = document.getElementById("zipFile");
  const repoUrl = document.getElementById("repoUrl");
  const feedback = document.getElementById("importFeedback");
  const helpTourBtn = document.getElementById("helpTourBtn");

  const filterSelect = document.getElementById("filterSelect");
  const projectContainer = document.querySelector(".row.row-cols-1");

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
  const flash = document.getElementById('flashMessage');
  if (flash) {
    flash.classList.remove('show'); // Bootstrap fade-out
    setTimeout(() => { if (flash) flash.remove(); }, 600);
  }
}, 4000);

  /** Intro.js Quick Tour (existing code) */
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
      { element: document.querySelector("[data-step='7']"), intro: "Download the project as a ZIP file for backup or sharing." },
      { element: document.querySelector("[data-step='8']"), intro: "Open this project directly in Visual Studio Code." }
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

  if (helpTourBtn) helpTourBtn.addEventListener("click", startTour);

  /** ZIP File Validation (existing code) */
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

  /** Repo URL Input (existing code) */
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

  /** Import Form Submit (existing code) */
  importForm.addEventListener("submit", e => {
    e.preventDefault();
    const file = zipFile.files[0];
    const url = repoUrl.value.trim();
    if (!file && !url) return;

    uploadSpinner.style.display = "inline-block";
    uploadBtn.disabled = true;
    feedback.innerHTML = `<div class="text-info">Processing...</div>`;

    const fetchUrl = file ? "/import" : "/clone";
    const body = file ? new FormData(importForm) : JSON.stringify({ repoUrl: url });
    const headers = file ? {} : { "Content-Type": "application/json" };

    fetch(fetchUrl, { method: "POST", body, headers })
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
        feedback.innerHTML = `<div class="text-danger">❌ Operation failed. ${err}</div>`;
        uploadBtn.disabled = false;
      });
  });

  /** -------------------------------
   * Filter / Sort Projects
   * ------------------------------- */
  if (filterSelect && projectContainer) {
    const parseDate = dateStr => {
      // Try to convert date string to Date object
      return new Date(dateStr.replace(" ", "T"));
    };

    const sortProjects = () => {
      const projects = Array.from(projectContainer.querySelectorAll(".col")).filter(col => col.querySelector(".project-card"));
      const value = filterSelect.value;

      projects.sort((a, b) => {
        const dateA = parseDate(a.querySelector(".project-date")?.innerText || "");
        const dateB = parseDate(b.querySelector(".project-date")?.innerText || "");
        return value === "latest" ? dateB - dateA : dateA - dateB;
      });

      projects.forEach(p => projectContainer.appendChild(p));
    };

    filterSelect.addEventListener("change", sortProjects);
    sortProjects(); // initial sort
  }
});
