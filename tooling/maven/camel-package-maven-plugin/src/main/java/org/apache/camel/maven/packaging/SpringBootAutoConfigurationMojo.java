/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.maven.packaging.model.ComponentOptionModel;
import org.apache.camel.maven.packaging.model.EndpointOptionModel;
import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.AnnotationSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.forge.roaster.model.source.PropertySource;
import org.jboss.forge.roaster.model.util.Strings;
import org.sonatype.plexus.build.incremental.BuildContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.apache.camel.maven.packaging.JSonSchemaHelper.getSafeValue;
import static org.apache.camel.maven.packaging.PackageHelper.loadText;

/**
 * Generate Spring Boot auto configuration files for Camel components.
 *
 * @goal prepare-spring-boot-auto-configuration
 */
public class SpringBootAutoConfigurationMojo extends AbstractMojo {

    /**
     * The maven project.
     *
     * @parameter property="project"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The project build directory
     *
     * @parameter default-value="${project.build.directory}"
     */
    protected File buildDir;

    /**
     * The source directory
     *
     * @parameter default-value="${basedir}/src/main/java"
     */
    protected File srcDir;

    /**
     * The resources directory
     *
     * @parameter default-value="${basedir}/src/main/resources"
     */
    protected File resourcesDir;

    /**
     * build context to check changed files and mark them for refresh (used for
     * m2e compatibility)
     *
     * @component
     * @readonly
     */
    private BuildContext buildContext;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // find the component names
        List<String> componentNames = findComponentNames();

        final Set<File> jsonFiles = new TreeSet<File>();
        PackageHelper.findJsonFiles(buildDir, jsonFiles, new PackageHelper.CamelComponentsModelFilter());

        // create auto configuration for the components
        if (!componentNames.isEmpty()) {
            getLog().info("Found " + componentNames.size() + " components");
            for (String componentName : componentNames) {
                String json = loadComponentJson(jsonFiles, componentName);
                if (json != null) {
                    ComponentModel model = generateComponentModel(componentName, json);

                    // use springboot as sub package name so the code is not in normal
                    // package so the Spring Boot JARs can be optional at runtime
                    int pos = model.getJavaType().lastIndexOf(".");
                    String pkg = model.getJavaType().substring(0, pos) + ".springboot";

                    createComponentConfigurationSource(pkg, model);
                    createComponentAutoConfigurationSource(pkg, model);
                    createSpringFactorySource(pkg, model);
                }
            }
        }
    }

    private void createSpringFactorySource(String packageName, ComponentModel model) throws MojoFailureException {
        StringBuilder sb = new StringBuilder();
        sb.append("org.springframework.boot.autoconfigure.EnableAutoConfiguration=\\\n");

        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("Component", "ComponentAutoConfiguration");
        sb.append(packageName).append(".").append(name).append("\n");

        String code = sb.toString();

        String fileName = "META-INF/spring.factories";
        File target = new File(resourcesDir, fileName);

        try {
            if (target.exists()) {
                String existing = FileUtils.readFileToString(target);
                if (!code.equals(existing)) {
                    // update
                    FileUtils.write(target, code);
                    getLog().info("Updated existing file: " + target);
                } else {
                    getLog().info("No changes to existing file: " + target);
                }
            } else {
                InputStream is = getClass().getClassLoader().getResourceAsStream("license-header.txt");
                String header = loadText(is);
                FileUtils.write(target, header);
                FileUtils.write(target, code, true);
                getLog().info("Created file: " + target);
            }
        } catch (Exception e) {
            throw new MojoFailureException("IOError with file " + target, e);
        }
    }

    private void createComponentConfigurationSource(String packageName, ComponentModel model) throws MojoFailureException {
        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);

        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("Component", "ComponentConfiguration");
        javaClass.setPackage(packageName).setName(name);

        if (!Strings.isBlank(model.getDescription())) {
            javaClass.getJavaDoc().setFullText(model.getDescription());
        }

        String prefix = "camel.component." + model.getScheme();
        javaClass.addAnnotation("org.springframework.boot.context.properties.ConfigurationProperties").setStringValue("prefix", prefix);

        for (ComponentOptionModel option : model.getComponentOptions()) {
            PropertySource<JavaClassSource> prop = javaClass.addProperty(option.getJavaType(), option.getName());
            if ("true".equals(option.getDeprecated())) {
                prop.getField().addAnnotation(Deprecated.class);
            }
            if (!Strings.isBlank(option.getDescription())) {
                prop.getField().getJavaDoc().setFullText(option.getDescription());
            }
        }

        String code = javaClass.toString();
        getLog().debug("Source code generated:\n" + code);

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";
        File target = new File(srcDir, fileName);

        try {
            if (target.exists()) {
                String existing = FileUtils.readFileToString(target);
                if (!code.equals(existing)) {
                    // update
                    FileUtils.write(target, code);
                    getLog().info("Updated existing file: " + target);
                } else {
                    getLog().info("No changes to existing file: " + target);
                }
            } else {
                InputStream is = getClass().getClassLoader().getResourceAsStream("license-header-java.txt");
                String header = loadText(is);
                FileUtils.write(target, header);
                FileUtils.write(target, code, true);
                getLog().info("Created file: " + target);
            }
        } catch (Exception e) {
            throw new MojoFailureException("IOError with file " + target, e);
        }
    }

    private void createComponentAutoConfigurationSource(String packageName, ComponentModel model) throws MojoFailureException {
        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);

        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("Component", "ComponentAutoConfiguration");
        javaClass.setPackage(packageName).setName(name);

        javaClass.addAnnotation(Configuration.class);

        String configurationName = name.replace("ComponentAutoConfiguration", "ComponentConfiguration");
        AnnotationSource<JavaClassSource> ann = javaClass.addAnnotation(EnableConfigurationProperties.class);
        ann.setLiteralValue("value", configurationName + ".class");

        // add method for auto configure

        javaClass.addImport("java.util.HashMap");
        javaClass.addImport("java.util.Map");
        javaClass.addImport(model.getJavaType());
        javaClass.addImport("org.apache.camel.CamelContext");
        javaClass.addImport("org.apache.camel.util.IntrospectionSupport");

        String body = createBody(model.getShortJavaType());

        MethodSource<JavaClassSource> method = javaClass.addMethod()
            .setName("configureComponent")
            .setPublic()
            .setBody(body)
            .setReturnType(model.getShortJavaType())
            .addThrows(Exception.class);

        method.addParameter("CamelContext", "camelContext");
        method.addParameter(configurationName, "configuration");

        method.addAnnotation(Bean.class);
        method.addAnnotation(ConditionalOnClass.class).setLiteralValue("value", "CamelContext.class");
        method.addAnnotation(ConditionalOnMissingBean.class).setLiteralValue("value", model.getShortJavaType() + ".class");

        String code = javaClass.toString();
        getLog().debug("Source code generated:\n" + code);

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";
        File target = new File(srcDir, fileName);

        try {
            if (target.exists()) {
                String existing = FileUtils.readFileToString(target);
                if (!code.equals(existing)) {
                    // update
                    FileUtils.write(target, code);
                    getLog().info("Updated existing file: " + target);
                } else {
                    getLog().info("No changes to existing file: " + target);
                }
            } else {
                InputStream is = getClass().getClassLoader().getResourceAsStream("license-header-java.txt");
                String header = loadText(is);
                FileUtils.write(target, header);
                FileUtils.write(target, code, true);
                getLog().info("Created file: " + target);
            }
        } catch (Exception e) {
            throw new MojoFailureException("IOError with file " + target, e);
        }
    }

    private String createBody(String shortJavaType) {
        StringBuilder sb = new StringBuilder();
        sb.append(shortJavaType).append(" component = new ").append(shortJavaType).append("();").append("\n");
        sb.append("component.setCamelContext(camelContext);\n");
        sb.append("\n");
        sb.append("Map<String, Object> parameters = new HashMap<>();\n");
        sb.append("IntrospectionSupport.getProperties(configuration, parameters, null);\n");
        sb.append("\n");
        sb.append("IntrospectionSupport.setProperties(camelContext, camelContext.getTypeConverter(), component, parameters);\n");
        sb.append("\n");
        sb.append("return component;");
        return sb.toString();
    }

    private String loadComponentJson(Set<File> jsonFiles, String componentName) {
        try {
            for (File file : jsonFiles) {
                if (file.getName().equals(componentName + ".json")) {
                    String json = loadText(new FileInputStream(file));
                    boolean isComponent = json.contains("\"kind\": \"component\"");
                    if (isComponent) {
                        return json;
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    private ComponentModel generateComponentModel(String componentName, String json) {
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("component", json, false);

        ComponentModel component = new ComponentModel();
        component.setScheme(JSonSchemaHelper.getSafeValue("scheme", rows));
        component.setSyntax(JSonSchemaHelper.getSafeValue("syntax", rows));
        component.setAlternativeSyntax(JSonSchemaHelper.getSafeValue("alternativeSyntax", rows));
        component.setTitle(JSonSchemaHelper.getSafeValue("title", rows));
        component.setDescription(JSonSchemaHelper.getSafeValue("description", rows));
        component.setLabel(JSonSchemaHelper.getSafeValue("label", rows));
        component.setDeprecated(JSonSchemaHelper.getSafeValue("deprecated", rows));
        component.setConsumerOnly(JSonSchemaHelper.getSafeValue("consumerOnly", rows));
        component.setProducerOnly(JSonSchemaHelper.getSafeValue("producerOnly", rows));
        component.setJavaType(JSonSchemaHelper.getSafeValue("javaType", rows));
        component.setGroupId(JSonSchemaHelper.getSafeValue("groupId", rows));
        component.setArtifactId(JSonSchemaHelper.getSafeValue("artifactId", rows));
        component.setVersion(JSonSchemaHelper.getSafeValue("version", rows));

        rows = JSonSchemaHelper.parseJsonSchema("componentProperties", json, true);
        for (Map<String, String> row : rows) {
            ComponentOptionModel option = new ComponentOptionModel();
            option.setName(getSafeValue("name", row));
            option.setKind(getSafeValue("kind", row));
            option.setType(getSafeValue("type", row));
            option.setJavaType(getSafeValue("javaType", row));
            option.setDeprecated(getSafeValue("deprecated", row));
            option.setDescription(getSafeValue("description", row));
            component.addComponentOption(option);
        }

        rows = JSonSchemaHelper.parseJsonSchema("properties", json, true);
        for (Map<String, String> row : rows) {
            EndpointOptionModel option = new EndpointOptionModel();
            option.setName(getSafeValue("name", row));
            option.setKind(getSafeValue("kind", row));
            option.setGroup(getSafeValue("group", row));
            option.setRequired(getSafeValue("required", row));
            option.setType(getSafeValue("type", row));
            option.setJavaType(getSafeValue("javaType", row));
            option.setEnums(getSafeValue("enum", row));
            option.setPrefix(getSafeValue("prefix", row));
            option.setMultiValue(getSafeValue("multiValue", row));
            option.setDeprecated(getSafeValue("deprecated", row));
            option.setDefaultValue(getSafeValue("defaultValue", row));
            option.setDescription(getSafeValue("description", row));
            component.addEndpointOption(option);
        }

        return component;
    }

    private List<String> findComponentNames() {
        List<String> componentNames = new ArrayList<String>();
        for (Resource r : project.getBuild().getResources()) {
            File f = new File(r.getDirectory());
            if (!f.exists()) {
                f = new File(project.getBasedir(), r.getDirectory());
            }
            f = new File(f, "META-INF/services/org/apache/camel/component");

            if (f.exists() && f.isDirectory()) {
                File[] files = f.listFiles();
                if (files != null) {
                    for (File file : files) {
                        // skip directories as there may be a sub .resolver directory
                        if (file.isDirectory()) {
                            continue;
                        }
                        String name = file.getName();
                        if (name.charAt(0) != '.') {
                            componentNames.add(name);
                        }
                    }
                }
            }
        }
        return componentNames;
    }

}
