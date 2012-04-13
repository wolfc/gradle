/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.plugins.signing

import org.gradle.api.Task
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.IConventionAware

import org.gradle.plugins.signing.signatory.*

import org.gradle.plugins.signing.type.SignatureType
import org.gradle.plugins.signing.type.SignatureTypeProvider
import org.gradle.plugins.signing.type.DefaultSignatureTypeProvider

import org.gradle.plugins.signing.signatory.pgp.PgpSignatoryProvider
import java.util.concurrent.Callable
import org.gradle.util.ConfigureUtil
import org.gradle.api.internal.Instantiator

/**
 * The global signing configuration for a project.
 */
class SigningExtension {
    
    /**
     * The name of the configuration that all signature artifacts will be placed into ("signatures")
     */
    static final String DEFAULT_CONFIGURATION_NAME = "signatures"
    
    /**
     * The project that the settings are for
     */
    final Project project
    
    /**
     * The configuration that signature artifacts will be placed into.
     * 
     * <p>Changing this will not affect any signing already configured.</p>
     */
    Configuration configuration

    private required = true

    /**
     * Whether or not this task should fail if no signatory or signature type are configured at generation time.
     *
     * If {@code required} is a {@link Callable}, it will be stored and "called" on demand (i.e. when {@link #isRequired()}
     * is called) and the return value will be interpreting according to the Groovy Truth. For example:
     *
     * <pre>
     * signing {
     *   required = { gradle.taskGraph.hasTask("uploadArchives") }
     * }
     * </pre>
     *
     * Because the task graph is not known until Gradle starts executing, we must use defer the decision. We can do this via using a
     * {@link Closure} (which is a {@link Callable}).
     *
     * For any other type, the value will be stored and evaluated on demand according to the Groovy Truth.
     *
     * <pre>
     * signing {
     *   required = false
     * }
     * </pre>
     */
    void setRequired(Object required) {
        this.required = required
    }

    /**
     * Whether or not this task should fail if no signatory or signature type are configured at generation time.
     * 
     * <p>Defaults to {@code true}.</p>
     *
     * @see #setRequired(Object)
     */
    boolean isRequired() {
        if (required instanceof Callable) {
            required.call()
        } else {
            required
        }
    }
    
    /**
     * The provider of signature types.
     */
    SignatureTypeProvider signatureTypes
    
    /**
     * The provider of signatories.
     */
    SignatoryProvider signatories
    
    /**
     * Configures the signing settings for the given project.
     */
    SigningExtension(Project project) {
        this.project = project
        this.configuration = getDefaultConfiguration()
        this.signatureTypes = createSignatureTypeProvider()
        this.signatories = createSignatoryProvider()

        project.tasks.withType(Sign) { task ->
            addSignatureSpecConventions(task)
        }
    }
    
    /**
     * Provides the configuration that signature artifacts are added to. Called once during construction.
     */
    protected Configuration getDefaultConfiguration() {
        def configurations = project.configurations
        def configuration = configurations.findByName(DEFAULT_CONFIGURATION_NAME)
        if (configuration == null) {
            configuration = configurations.add(DEFAULT_CONFIGURATION_NAME)
        }
        configuration
    }
    
    /**
     * Provides the signature type provider. Called once during construction.
     */
    protected SignatureTypeProvider createSignatureTypeProvider() {
        new DefaultSignatureTypeProvider()
    }
    
    /**
     * Provides the signatory provider. Called once during construction.
     */
    protected SignatoryProvider createSignatoryProvider() {
        new PgpSignatoryProvider()
    }
    
    /**
     * Configures the signatory provider (delegating to its {@link SignatoryProvider#configure(SigningExtension,Closure) configure method}).
     * 
     * @param closure the signatory provider configuration DSL
     * @return the configured signatory provider
     */
    SignatoryProvider signatories(Closure closure) {
        signatories.configure(this, closure)
        signatories
    }
    
    /**
     * The signatory that will be used for signing when an explicit signatory has not been specified.
     * 
     * <p>Delegates to the signatory provider's default signatory.</p>
     */
    Signatory getSignatory() {
        signatories.getDefaultSignatory(project)
    }
    
    /**
     * The signature type that will be used for signing files when an explicit signature type has not been specified.
     * 
     * <p>Delegates to the signature type provider's default type.</p>
     */
    SignatureType getSignatureType() {
        signatureTypes.defaultType
    }
    
    /**
     * The configuration that signature artifacts are added to.
     */
    Configuration getConfiguration() {
        configuration
    }

    /**
     * Adds conventions to the given spec, using this settings object's default signatory and signature type as the
     * default signatory and signature type for the spec.
     */
    protected void addSignatureSpecConventions(SignatureSpec spec) {
        if (!(spec instanceof IConventionAware)) {
            throw new InvalidUserDataException("Cannot add conventions to signature spec '$spec' as it is not convention aware")
        }
        
        spec.conventionMapping.map('signatory') { getSignatory() }
        spec.conventionMapping.map('signatureType') { getSignatureType() }
        spec.conventionMapping.required = { isRequired() }
    }
    
    /**
     * Creates signing tasks that depend on and sign the "archive" produced by the given tasks.
     *
     * <p>The created tasks will be named "sign<i>&lt;input task name capitalized&gt;</i>". That is, given a task with the name "jar"
     * the created task will be named "signJar".
     * <p>
     * If the task is not an {@link org.gradle.api.tasks.bundling.AbstractArchiveTask}, an 
     * {@link org.gradle.api.InvalidUserDataException} will be thrown.</p>
     * <p>
     * The signature artifact for the created task is added to the {@link #getConfiguration() for this settings object}.
     *
     * @param task The tasks whose archives are to be signed
     * @return the created tasks.
     */
    List<Sign> sign(Task... tasksToSign) {
        tasksToSign.collect { taskToSign ->
            def signTask = project.task("sign${taskToSign.name.capitalize()}", type: Sign) {
                sign taskToSign
            }
            addSignaturesToConfiguration(signTask, getConfiguration())
            signTask
        }
    }
    
    /**
     * Creates signing tasks that sign {@link Configuration#getAllArtifacts() all of the artifacts} of the given configurations.
     *
     * <p>The created tasks will be named "sign<i>&lt;configuration name capitalized&gt;</i>". That is, given a configuration with the name "archives"
     * the created task will be named "signArchives".
     *
     * The signature artifact for the created task is added to the {@link #getConfiguration() for this settings object}.
     *
     * @param task The configurations whose archives are to be signed
     * @return the created tasks.
     */
    List<Sign> sign(Configuration... configurations) {
        configurations.collect { configuration ->
            def signTask = project.task("sign${configuration.name.capitalize()}", type: Sign) {
                sign configuration
            }
            this.addSignaturesToConfiguration(signTask, getConfiguration())
            signTask
        }
    }
    
    protected addSignaturesToConfiguration(Sign task, Configuration configuration) {
        task.signatures.all { configuration.artifacts.add(it) }
        task.signatures.whenObjectRemoved { configuration.artifacts.remove(it) }
    }

    /**
     * Digitally signs the publish artifacts, generating signature files alongside them.
     *
     * <p>The project's default signatory and default signature type from the
     * {@link SigningExtension signing settings} will be used to generate the signature.
     * The returned {@link SignOperation sign operation} gives access to the created signature files.
     * <p>
     * If there is no configured default signatory available, the sign operation will fail.
     *
     * @param publishArtifacts The publish artifacts to sign
     * @return The executed {@link SignOperation sign operation}
     */
    SignOperation sign(PublishArtifact... publishArtifacts) {
        doSignOperation {
            sign(*publishArtifacts)
        }
    }

    /**
     * Digitally signs the files, generating signature files alongside them.
     *
     * <p>The project's default signatory and default signature type from the
     * {@link SigningExtension signing settings} will be used to generate the signature.
     * The returned {@link SignOperation sign operation} gives access to the created signature files.
     * <p>
     * If there is no configured default signatory available, the sign operation will fail.
     *
     * @param publishArtifacts The files to sign.
     * @return The executed {@link SignOperation sign operation}.
     */
    SignOperation sign(File... files) {
        doSignOperation {
            sign(*files)
        }
    }

    /**
     * Digitally signs the files, generating signature files alongside them.
     *
     * <p>The project's default signatory and default signature type from the
     * {@link SigningExtension signing settings} will be used to generate the signature.
     * The returned {@link SignOperation sign operation} gives access to the created signature files.
     * <p>
     * If there is no configured default signatory available, the sign operation will fail.
     *
     * @param classifier The classifier to assign to the created signature artifacts.
     * @param publishArtifacts The publish artifacts to sign.
     * @return The executed {@link SignOperation sign operation}.
     */
    SignOperation sign(String classifier, File... files) {
        doSignOperation {
            sign(classifier, *files)
        }
    }

    /**
     * Creates a new {@link SignOperation sign operation} using the given closure to configure it before executing it.
     *
     * <p>The project's default signatory and default signature type from the
     * {@link SigningExtension signing settings} will be used to generate the signature.
     * The returned {@link SignOperation sign operation} gives access to the created signature files.
     * <p>
     * If there is no configured default signatory available (and one is not explicitly specified in this operation's configuration),
     * the sign operation will fail.
     *
     * @param closure The configuration of the {@link SignOperation sign operation}.
     * @return The executed {@link SignOperation sign operation}.
     */
    SignOperation sign(Closure closure) {
        doSignOperation(closure)
    }

    protected SignOperation doSignOperation(Closure setup) {
        def operation = project.services.get(Instantiator).newInstance(SignOperation)
        addSignatureSpecConventions(operation)
        operation.configure(setup)
        operation.execute()
        operation
    }
}
