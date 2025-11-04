package com.aem.builder.service.impl;

import com.aem.builder.service.ArtifactService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ArtifactServiceImpl implements ArtifactService {

    private static final Path BASE_DIR = Paths.get("src/main/java/com/aem/builder/alljavaclasses");
    private static final Path PROJECTS_DIR = Paths.get("generated-projects");

    @Override
    public Map<String, List<ArtifactFile>> listArtifacts() throws IOException {
        if (!Files.exists(BASE_DIR)) {
            throw new IOException("Artifact source directory not found: " + BASE_DIR);
        }

        try (var paths = Files.walk(BASE_DIR)) {
            return paths
                    .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .map(this::mapToArtifact)
                    .filter(Objects::nonNull)
                    .collect(Collectors.groupingBy(
                            file -> extractCategory(file.relativePath),
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));
        }
    }

    private ArtifactFile mapToArtifact(Path path) {
        Path relativePath = BASE_DIR.relativize(path);
        String name = path.getFileName().toString();
        String packageInfo = relativePath.getParent() != null
                ? relativePath.getParent().toString().replace("/", ".")
                : "";
        return new ArtifactFile(name, relativePath.toString(), packageInfo);
    }

    private String extractCategory(String path) {
        path = path.toLowerCase();
        if (path.contains("servlet")) return "Servlets";
        if (path.contains("handler")) return "Handlers";
        if (path.contains("model")) return "Models";
        if (path.contains("service")) return "Services";
        if (path.contains("listener")) return "Listeners";
        if (path.contains("scheduler")) return "Schedulers";
        return "Misc";
    }

    @Override
    public void generateArtifacts(String projectName, List<String> selectedFiles) throws IOException {
        Path coreJavaBase = PROJECTS_DIR
                .resolve(projectName)
                .resolve("core/src/main/java");

        if (!Files.exists(coreJavaBase)) {
            throw new IOException("Project folder not found: " + coreJavaBase);
        }

        String basePackage = detectExistingBasePackage(coreJavaBase);
        System.out.println("Detected base package: " + basePackage);

        for (String relativePathStr : selectedFiles) {
            Path sourcePath = BASE_DIR.resolve(relativePathStr);
            if (!Files.exists(sourcePath)) {
                System.out.println("Skipping missing file: " + sourcePath);
                continue;
            }

            String subFolder = detectTargetFolder(relativePathStr);
            Path targetDir = coreJavaBase.resolve(basePackage.replace(".", "/")).resolve(subFolder);
            Files.createDirectories(targetDir);

            Path targetPath = targetDir.resolve(sourcePath.getFileName());
            if (Files.exists(targetPath)) {
                System.out.println("Already exists, skipping: " + targetPath);
                continue;
            }

            String content = Files.readString(sourcePath, StandardCharsets.UTF_8);
            String updatedContent = replacePackage(content, basePackage + "." + subFolder);

            Files.writeString(targetPath, updatedContent, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            System.out.println("Copied " + sourcePath.getFileName() + " → " + targetPath);
        }
    }

    private String detectExistingBasePackage(Path javaBasePath) throws IOException {
        Pattern packagePattern = Pattern.compile("^\\s*package\\s+([a-zA-Z0-9_.]+);");

        try (var stream = Files.walk(javaBasePath)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                List<String> lines = Files.readAllLines(path);
                for (String line : lines) {
                    Matcher matcher = packagePattern.matcher(line);
                    if (matcher.find()) {
                        String pkg = matcher.group(1);
                        if (pkg.contains(".core.")) {
                            return pkg.substring(0, pkg.indexOf(".core.") + 5);
                        } else if (pkg.endsWith(".core")) {
                            return pkg;
                        }
                    }
                }
            }
        }

        // fallback
        return "com.aem." + javaBasePath.getFileName().toString() + ".core";
    }

    private String detectTargetFolder(String relativePath) {
        relativePath = relativePath.toLowerCase();

        if (relativePath.contains("servlet")) return "servlets";
        if (relativePath.contains("handler")) return "handlers";
        if (relativePath.contains("model")) return "models";
        if (relativePath.contains("service")) return "services";
        if (relativePath.contains("filter")) return "filters";
        if (relativePath.contains("listener")) return "listeners";
        if (relativePath.contains("scheduler")) return "schedulers";
        if (relativePath.contains("workflow")) return "workflows";

        Path relative = Paths.get(relativePath);
        Path parent = relative.getParent();
        if (parent != null) {
            return parent.getFileName().toString();
        }
        return "";
    }

    private String replacePackage(String content, String newPackage) {
        return content.replaceFirst(
                "package\\s+com\\.aem\\.builder(\\.[^;]*)?;",
                "package " + newPackage + ";"
        );
    }

    // =====================================================================================
    // Dynamic generator
    // =====================================================================================
    @Override
    public void generateDynamicArtifact(String projectName, String type, Map<String, String> params) throws IOException {
        String basePackage = getProjectBasePackage(projectName);
        String className = params.getOrDefault("name", "My" + type);
        Path targetDir;
        String content;

        switch (type.toLowerCase()) {
            case "servlet":
                targetDir = getCorePath(projectName).resolve("servlets");
                content = generateServletCode(basePackage, className,
                        params.getOrDefault("method", "GET"),
                        params.getOrDefault("path", "/bin/" + className.toLowerCase()));
                break;

            case "scheduler":
                targetDir = getCorePath(projectName).resolve("schedulers");
                content = generateSchedulerCode(basePackage, className,
                        params.getOrDefault("cron", "0 0 * * * ?"));
                break;

            case "listener":
                targetDir = getCorePath(projectName).resolve("listeners");
                content = generateListenerCode(basePackage, className,
                        params.getOrDefault("eventType", "resource"));
                break;

            case "service":
                targetDir = getCorePath(projectName).resolve("services");
                content = generateServiceCode(basePackage, className,
                        params.getOrDefault("interfaceType", "Component"));
                break;

            default:
                throw new IllegalArgumentException("Unknown artifact type: " + type);
        }

        Files.createDirectories(targetDir);
        Path targetPath = targetDir.resolve(className + ".java");
        if (Files.exists(targetPath)) {
            // If exists, overwrite or skip — current behaviour: overwrite with new content
            System.out.println("Overwriting existing generated artifact: " + targetPath);
        }
        Files.writeString(targetPath, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        System.out.println("Generated " + type + ": " + className + " -> " + targetPath);
    }

    // base package detection for newly generated artifacts
    private String getProjectBasePackage(String projectName) {
        return "com.aem." + projectName + ".core";
    }

    private Path getCorePath(String projectName) {
        return PROJECTS_DIR
                .resolve(projectName)
                .resolve("core/src/main/java/com/aem")
                .resolve(projectName)
                .resolve("core");
    }

    // Templates
    private String generateServletCode(String basePackage, String className, String method, String path) {
        String methodName = method == null ? "GET" : method;
        String doMethod = switch (methodName.toUpperCase()) {
            case "POST" -> "Post";
            case "PUT" -> "Put";
            case "DELETE" -> "Delete";
            default -> "Get";
        };
        return String.format("""
                package %s.servlets;

                import org.apache.sling.api.servlets.SlingAllMethodsServlet;
                import org.apache.sling.api.SlingHttpServletRequest;
                import org.apache.sling.api.SlingHttpServletResponse;
                import org.osgi.service.component.annotations.Component;

                import javax.servlet.Servlet;
                import java.io.IOException;

                @Component(
                    service = { Servlet.class },
                    property = {
                        "sling.servlet.methods=%s",
                        "sling.servlet.paths=%s"
                    }
                )
                public class %s extends SlingAllMethodsServlet {

                    @Override
                    protected void do%s(SlingHttpServletRequest request, SlingHttpServletResponse response)
                            throws IOException {
                        response.getWriter().write("Generated %s");
                    }
                }
                """, basePackage, methodName, path, className, doMethod, className);
    }

    private String generateSchedulerCode(String basePackage, String className, String cron) {
        return String.format("""
                package %s.schedulers;

                import org.apache.sling.commons.scheduler.ScheduleOptions;
                import org.apache.sling.commons.scheduler.Scheduler;
                import org.osgi.service.component.annotations.Activate;
                import org.osgi.service.component.annotations.Component;
                import org.osgi.service.component.annotations.Deactivate;
                import org.osgi.service.component.annotations.Reference;

                @Component(service = Runnable.class, immediate = true)
                public class %s implements Runnable {

                    @Reference
                    private Scheduler scheduler;

                    private static final String JOB_NAME = "%sJob";

                    @Activate
                    protected void activate() {
                        ScheduleOptions options = scheduler.EXPR("%s");
                        options.name(JOB_NAME);
                        scheduler.schedule(this, options);
                    }

                    @Deactivate
                    protected void deactivate() {
                        scheduler.unschedule(JOB_NAME);
                    }

                    @Override
                    public void run() {
                        System.out.println("Scheduler %s running");
                    }
                }
                """, basePackage, className, className, cron, className);
    }

    private String generateListenerCode(String basePackage, String className, String eventType) {
        return String.format("""
                package %s.listeners;

                import org.osgi.service.component.annotations.Component;
                import org.osgi.service.event.Event;
                import org.osgi.service.event.EventHandler;

                @Component(
                    service = EventHandler.class,
                    property = {
                        "event.topics=%s"
                    }
                )
                public class %s implements EventHandler {
                    @Override
                    public void handleEvent(Event event) {
                        System.out.println("Listener %s triggered for event: " + event.getTopic());
                    }
                }
                """, basePackage, eventType, className, className);
    }

    private String generateServiceCode(String basePackage, String className, String interfaceType) {
        return String.format("""
                package %s.services;

                import org.osgi.service.component.annotations.Component;

                @Component(service = %s.class, immediate = true)
                public class %s implements %s {
                    @Override
                    public void execute() {
                        System.out.println("Service %s executed");
                    }
                }

                interface %s {
                    void execute();
                }
                """, basePackage, className, className, className, className, className);
    }
}
