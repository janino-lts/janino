
/*
 * Janino - An embedded Java[TM] compiler
 *
 * Copyright (c) 2001-2017 Arno Unkrig. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *       following disclaimer.
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *       following disclaimer in the documentation and/or other materials provided with the distribution.
 *    3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote
 *       products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.codehaus.commons.compiler;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;

import org.codehaus.commons.nullanalysis.NotNullByDefault;
import org.codehaus.commons.nullanalysis.Nullable;

/**
 * Executes a {@link PrivilegedAction} or {@link PrivilegedExceptionAction} in a context with restricted permissions.
 * This is useful for executing "untrusted" code, e.g. user-provided expressions or scripts that were compiled with
 * <a href="https://janino.unkrig.de/">JANINO</a>.
 * <p>
 *   Code example:
 * </p>
 * <pre>
 *     Permissions noPermissions = new Permissions();
 *     Sandbox sandbox = new Sandbox(noPermissions);
 *     sandbox.confine(new PrivilegedExceptionAction&lt;Object&gt;() {
 *         &#64;Override public Object run() throws Exception { new java.io.File("xxx").delete(); return null; }
 *     });
 * </pre>
 * <p>
 *   The sandbox relies on the security manager, which is not available on all JVMs:
 * </p>
 * <ul>
 *   <li>Java 8 through 17: Supported.</li>
 *   <li>
 *     Java 18 through 23: Supported only if the JVM was started with {@code -Djava.security.manager=allow}, or if
 *     a security manager is already installed.
 *   </li>
 *   <li>Java 24 and later: Not supported (the security manager was permanently disabled).</li>
 * </ul>
 * <p>
 *   Use {@link #isSupported()} to check whether the sandbox can be used on the running JVM. If it cannot, the {@link
 *   #Sandbox(PermissionCollection) constructor} throws an {@link UnsupportedOperationException}.
 * </p>
 * <p>
 *   <b>Notice:</b> Code that is confined by the sandbox can call {@code AccessController.doPrivileged()} itself,
 *   and then runs with the permissions of its own protection domain. By default, JANINO defines the generated classes
 *   with the protection domain of the compiler, and the policy that this class installs grants all permissions to
 *   that domain, so that the confined code can escape from the sandbox. To prevent that, define the generated classes
 *   with the same permissions as the sandbox, through a protection domain with <em>static</em> permissions:
 * </p>
 * <pre>
 *     Sandbox sandbox = new Sandbox(permissions);
 *     scriptEvaluator.setProtectionDomain(new ProtectionDomain(null, permissions));
 *     scriptEvaluator.cook(script);
 *     sandbox.confine(...);
 * </pre>
 * <p>
 *   See {@link ICookable#setProtectionDomain(ProtectionDomain)}, and, for the {@link AbstractJavaSourceClassLoader},
 *   {@link AbstractJavaSourceClassLoader#setProtectionDomainFactory(
 *   AbstractJavaSourceClassLoader.ProtectionDomainFactory)}.
 *   Alternatively, a {@link org.codehaus.commons.compiler.sandbox.SandboxPolicy} rejects such code already at
 *   compile time.
 * </p>
 *
 * @see <a href="https://docs.oracle.com/javase/tutorial/essential/environment/security.html">ORACLE: Java Essentials:
 *      The Security Manager</a>
 */
public final
class Sandbox {

    /**
     * The reason why the security manager cannot be used on the running JVM, or {@code null} iff it can be used.
     */
    @Nullable private static final UnsupportedOperationException UNSUPPORTED_REASON = Sandbox.installSecurityManager();

    /**
     * Installs a security manager (with an "all permissions" policy), unless a security manager is already installed.
     *
     * @return The reason why the security manager cannot be used, or {@code null} iff it is installed
     */
    @Nullable private static UnsupportedOperationException
    installSecurityManager() {

        if (System.getSecurityManager() != null) return null;

        // Before installing the security manager, configure a decent ("positive") policy. Otherwise a policy is
        // determine automatically as follows:
        // (1) If seccurity property "policy.provider" is set: Load a class with that name, and cast it to "Policy".
        // (2) Otherwise, use class "sun.security.provider.PolicyFile" as the policy. That class reads a plethora
        //     of "*.policy" files:
        //         jre/lib/security/java[ws].policy     (Java 6, 8)
        //         conf/security/javaws.policy          (Java 9)
        //         conf/security/java.policy            (Java 9, 10, 11, 12)
        //         conf/security/policy/[un]limited/**  (Java 9, 10, 11, 12)
        //         lib/security/default.policy          (Java 9, 10, 11, 12)
        //     That eventually leads to a very restricted policy which typically allows applications to read only
        //     a small set of system properties and nothing else.
        Policy previousPolicy = Policy.getPolicy();
        try {
            Policy.setPolicy(new Policy() {

                @Override @NotNullByDefault(false) public PermissionCollection
                getPermissions(CodeSource codesource) {

                    // Taken from https://github.com/elastic/elasticsearch/pull/14274, on request of
                    // https://github.com/janino-compiler/janino/issues/124:

                    // Code should not rely on this method, or at least use it correctly:
                    // https://bugs.openjdk.java.net/browse/JDK-8014008
                    // return them a new empty permissions object so jvisualvm etc work
                    for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                        if (
                            "sun.rmi.server.LoaderHandler".equals(element.getClassName())
                            && "loadClass".equals(element.getMethodName())
                        ) return new Permissions();
                    }

                    // Return UNSUPPORTED_EMPTY_COLLECTION since it is safe.
                    return super.getPermissions(codesource);
                }

                @Override @NotNullByDefault(false) public boolean
                implies(ProtectionDomain domain, Permission permission) { return true; }
            });
        } catch (UnsupportedOperationException uoe) {

            // Java 24+: The security manager is permanently disabled.
            return uoe;
        }

        try {
            System.setSecurityManager(new SecurityManager());
        } catch (UnsupportedOperationException uoe) {

            // Java 18 through 23 without "-Djava.security.manager=allow": Undo the policy change.
            Policy.setPolicy(previousPolicy);
            return uoe;
        }

        return null;
    }

    private final AccessControlContext accessControlContext;

    /**
     * @return Whether the sandbox can be used on the running JVM, i.e. whether a security manager is installed
     */
    public static boolean
    isSupported() { return Sandbox.UNSUPPORTED_REASON == null; }

    /**
     * @param permissions                    Will be applied on later calls to {@link #confine(PrivilegedAction)} and
     *                                       {@link #confine(PrivilegedExceptionAction)}
     * @throws UnsupportedOperationException The running JVM does not support the security manager (see {@link
     *                                       #isSupported()})
     */
    public
    Sandbox(PermissionCollection permissions) {

        UnsupportedOperationException unsupportedReason = Sandbox.UNSUPPORTED_REASON;
        if (unsupportedReason != null) {
            throw new UnsupportedOperationException((
                "The sandbox requires a security manager, which is not available on this JVM (Java "
                + System.getProperty("java.specification.version")
                + "); it is supported on Java 8 through 17, and on Java 18 through 23 with "
                + "\"-Djava.security.manager=allow\""
            ), unsupportedReason);
        }

        this.accessControlContext = new AccessControlContext(new ProtectionDomain[] {
            new ProtectionDomain(null, permissions)
        });
    }

    /**
     * Runs the given <var>action</var>, confined by the permissions configured through the {@link
     * #Sandbox(PermissionCollection) constructor}.
     *
     * @return The value returned by the <var>action</var>
     */
    public <R> R
    confine(PrivilegedAction<R> action) {
        return AccessController.doPrivileged(action, this.accessControlContext);
    }

    public <R> R
    confine(PrivilegedExceptionAction<R> action) throws Exception {
        try {
            return AccessController.doPrivileged(action, this.accessControlContext);
        } catch (PrivilegedActionException pae) {
            throw pae.getException();
        }
    }
}
