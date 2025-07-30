# 🛠️ AEM Project Generator

## 📌 Overview

**AEM Project Generator** is a web-based tool inspired by [Spring Initializr](https://start.spring.io/), designed to streamline the creation of Adobe Experience Manager (AEM) project structures. It empowers developers to easily generate a fully configured AEM Maven project by filling out a form and selecting predefined components and templates.

The application is built using **Spring Boot** and provides a guided multi-step form UI to input project metadata, select components/templates, and generate a ready-to-use zipped AEM project scaffold.

---

## 🎯 Goal

The goal of this tool is to **eliminate the manual and repetitive effort** involved in setting up the initial AEM project structure. It allows developers and solution architects to:

- Quickly prototype AEM projects
- Maintain consistency across different AEM initiatives
- Simplify onboarding for new developers
- Enable generation for both AEM 6.5.x and AEM Cloud Service compatible projects

---

## 🧩 Features

### ✅ Step-by-Step Project Creation Flow

#### 🔹 Page 1: Project Details
Collects the foundational details for your AEM project:

- **Project Name**: Human-readable title (e.g., `demo Project`)
- **Project ID**: Machine name identifier (e.g., `demoproject`)
- **Group ID**: Java package-style naming (e.g., `com.aem.demo`)
- **AEM Version**: Dropdown (`6.5.13`, `Cloud Service`)
- **Country**: ISO country code (`us`, `de`, etc.)
- **Language**: ISO language code (`en`, `fr`, etc.)
- **Include Dispatcher Config?**: Checkbox for including dispatcher module

#### 🔹 Page 2: Component Selection
Allows users to choose from predefined AEM components:

- Hero Banner  
- Image Card  
- Accordion  
- Call To Action Card  
- Carousel  
- etc.

Supports **multi-select** functionality.

#### 🔹 Page 3: Template Selection
Lets users pick from predefined AEM page templates:

- Landing Page  
- Article Page  
- Product Detail Page  
- Blog Page  
- etc.

Supports **multi-select** functionality.

#### 🔹 Page 4: Summary & Generate
- Displays a full summary of user inputs.
- On click of the **Generate** button:
  - Builds a **Maven multi-module AEM project structure**:
    - `core` (Java backend logic)
    - `ui.apps` (HTL and clientlibs)
    - `ui.content` (content and pages)
    - `ui.config` (OSGi configs)
    - `dispatcher` (Apache configs — optional)
  - Injects selected components into:  
    `/apps/<project-id>/components`
  - Injects selected templates into:  
    `/apps/<project-id>/templates`
  - Packages and downloads a **.zip** file containing the complete AEM project.

---

## ⚙️ Technology Stack

| Layer       | Technology         |
|-------------|--------------------|
| Backend     | Spring Boot        |
| Build Tool  | Maven              |
| Frontend    | Thymeleaf / React (optional upgrade) |
| Packaging   | ZipOutputStream / Apache Commons Compress |
| Project Gen | Maven Archetype / Template Engine (e.g., Freemarker/Velocity) |

---

## 🚀 Getting Started

### 🧾 Prerequisites
- Java 17+
- Maven 3.6+
- Git (for archetype cloning if needed)

### 🛠️ Run the Application
```bash
git clone https://github.com/your-org/aem-project-generator.git
cd aem-project-generator
./mvnw spring-boot:run
```

## 🔧 Supported Dialog Field Types

The component generator supports a variety of Touch UI dialog fields. The table below lists the available types and the Granite resource types used during generation:

| Type | Granite Resource |
|------|-----------------|
| textfield | granite/ui/components/coral/foundation/form/textfield |
| textarea | granite/ui/components/coral/foundation/form/textarea |
| numberfield | granite/ui/components/coral/foundation/form/numberfield |
| hidden | granite/ui/components/coral/foundation/form/hidden |
| checkbox | granite/ui/components/coral/foundation/form/checkbox |
| checkboxgroup | granite/ui/components/coral/foundation/form/checkboxgroup |
| radiogroup | granite/ui/components/coral/foundation/form/radiogroup |
| select | granite/ui/components/coral/foundation/form/select |
| multiselect | granite/ui/components/coral/foundation/form/multifield |
| pathfield | granite/ui/components/coral/foundation/form/pathfield |
| autocomplete | granite/ui/components/coral/foundation/form/autocomplete |
| datepicker | granite/ui/components/coral/foundation/form/datepicker |
| image | cq/gui/components/authoring/dialog/fileupload |
| fileupload | cq/gui/components/authoring/dialog/fileupload |
| richtext | cq/gui/components/authoring/dialog/richtext |
| multifield | granite/ui/components/coral/foundation/form/multifield |
| nestedmultifield | granite/ui/components/coral/foundation/form/multifield |
| tabs | granite/ui/components/coral/foundation/tabs |
| button | granite/ui/components/coral/foundation/form/button |
| password | granite/ui/components/coral/foundation/form/password |
| switch | granite/ui/components/coral/foundation/form/switch |
| anchorbrowser | cq/gui/components/coral/common/form/anchorbrowser |
| tagfield | cq/gui/components/coral/common/form/tagfield |

## 🌐 API snippets

* `GET /projects` – returns all available projects detected under `generated-projects/`.
