document.addEventListener("DOMContentLoaded", () => {
  const importForm = document.getElementById("importForm");
  const uploadBtn = document.getElementById("uploadBtn");
  const uploadSpinner = document.getElementById("uploadSpinner");
  const zipFile = document.getElementById("zipFile");
  const repoUrl = document.getElementById("repoUrl");
  const feedback = document.getElementById("importFeedback");
  const filterSelect = document.getElementById("filterSelect");
  const projectContainer = document.getElementById("projectContainer");

  // Tooltip init
  var tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
  tooltipTriggerList.map(el => new bootstrap.Tooltip(el));

  // Make project card clickable
  document.querySelectorAll(".project-card").forEach(card => {
    card.style.cursor = "pointer";
    card.addEventListener("click", e => {
      if (e.target.closest("button, a, form")) return;
      const path = card.getAttribute("data-path");
      if (path) window.location.href = path;
    });
  });

  // Flash message auto-fade
  setTimeout(() => {
    const flash = document.getElementById('flashMessage');
    if (flash) {
      flash.classList.remove('show');
      setTimeout(() => { if (flash) flash.remove(); }, 600);
    }
  }, 4000);

  // Intro.js Tour
  function getTourSteps() {
    const steps = [];
    const createBtn = document.querySelector("a[href='/create']");
    const importBtn = document.querySelector("button[data-bs-target='#importModal']");
    if (createBtn) steps.push({ element: createBtn, intro: "Click here to create a new AEM project." });
    if (importBtn) steps.push({ element: importBtn, intro: "You can also import existing projects from ZIP or Git." });

    const firstCard = document.querySelector(".project-card");
    if (firstCard) {
      const viewBtn = firstCard.querySelector("a.btn-outline-primary");
      const folderBtn = firstCard.querySelector("a.btn-outline-warning");
      const downloadBtn = firstCard.querySelector("a.btn-outline-success");
      const vscodeBtn = firstCard.querySelector("form button.btn-outline-dark");

      steps.push({ element: firstCard, intro: "This project provides quick actions — let's explore them!" });
      if (viewBtn) steps.push({ element: viewBtn, intro: "Click to view this project's full structure and details." });
      if (folderBtn) steps.push({ element: folderBtn, intro: "Open the folder where this project is stored on your system." });
      if (downloadBtn) steps.push({ element: downloadBtn, intro: "Download the project as a ZIP file for backup or sharing." });
      if (vscodeBtn) steps.push({ element: vscodeBtn, intro: "Open this project directly in Visual Studio Code." });
    }

    return steps;
  }

  function startTour() {
    const steps = getTourSteps();
    if (!steps.length) return;

    introJs().setOptions({
      steps: steps,
      showProgress: true,
      showBullets: false,
      exitOnOverlayClick: false,
      nextLabel: "Next →",
      prevLabel: "← Back",
      doneLabel: "Got it!"
    }).start();
  }

  if (!localStorage.getItem("aemDashboardTourShown")) {
    setTimeout(() => {
      startTour();
      localStorage.setItem("aemDashboardTourShown", "true");
    }, 1000);
  }

  // ZIP Validation
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
          feedback.innerHTML = `<div class="text-danger"> ❌ ${data.error}</div>`;
        }
      })
      .catch(() => {
        feedback.innerHTML = `<div class="text-danger"> ❌ Validation failed. Please try again.</div>`;
      });
  });

  // Repo URL Input
  repoUrl.addEventListener("input", () => {
    const url = repoUrl.value.trim();
    feedback.innerHTML = "";
    zipFile.value = "";
    if (url === "") {
      uploadBtn.disabled = true;
      uploadBtn.innerHTML = `<i class="bi bi-cloud-arrow-up me-1"></i> Upload`;
    } else {
      uploadBtn.disabled = false;
      uploadBtn.innerHTML = `<i class="bi bi-git me-1"></i> Clone`;
    }
  });

  // Import Form Submit
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

  // Filter / Sort Projects
  if (filterSelect && projectContainer) {
    const parseDate = dateStr => new Date(dateStr.replace(" ", "T"));

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
    sortProjects();
  }
});
