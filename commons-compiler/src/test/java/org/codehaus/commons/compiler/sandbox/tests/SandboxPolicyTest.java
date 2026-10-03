/*
 * Janino - An embedded Java[TM] compiler
 *
 * Copyright (c) 2026 Stefan Zobel. All rights reserved.
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

package org.codehaus.commons.compiler.sandbox.tests;

import org.codehaus.commons.compiler.sandbox.MemberRef;
import org.codehaus.commons.compiler.sandbox.SandboxPolicy;
import org.junit.Assert;
import org.junit.Test;

// SUPPRESS CHECKSTYLE Javadoc:9999

public
class SandboxPolicyTest {

    @SuppressWarnings("static-method") @Test public void
    testEmptyPolicyAllowsNothing() {
        SandboxPolicy policy = SandboxPolicy.builder().build();
        Assert.assertFalse(policy.isAllowed(MemberRef.method("java.lang.String", "length", "()I")));
        Assert.assertFalse(policy.isAllowed(MemberRef.method("java.lang.Object", "<init>", "()V")));
        Assert.assertFalse(policy.isSubclassingAllowed("java.lang.Object"));
    }

    @SuppressWarnings("static-method") @Test public void
    testAllowMethod() {
        SandboxPolicy policy = SandboxPolicy.builder().allowMethod("pkg.Foo", "meth", "(I)V").build();
        Assert.assertTrue(policy.isAllowed(MemberRef.method("pkg.Foo", "meth", "(I)V")));
        Assert.assertFalse(policy.isAllowed(MemberRef.method("pkg.Foo", "meth", "(J)V")));
        Assert.assertFalse(policy.isAllowed(MemberRef.method("pkg.Bar", "meth", "(I)V")));
        Assert.assertFalse(policy.isAllowed(MemberRef.field("pkg.Foo", "meth", "I")));
    }

    @SuppressWarnings("static-method") @Test public void
    testAllowMethods() {
        SandboxPolicy policy = SandboxPolicy.builder().allowMethods("pkg.Foo", "meth1", "meth2").build();
        Assert.assertTrue(policy.isAllowed(MemberRef.method("pkg.Foo", "meth1", "(I)V")));
        Assert.assertTrue(policy.isAllowed(MemberRef.method("pkg.Foo", "meth1", "(J)V")));
        Assert.assertTrue(policy.isAllowed(MemberRef.method("pkg.Foo", "meth2", "()V")));
        Assert.assertFalse(policy.isAllowed(MemberRef.method("pkg.Foo", "meth3", "()V")));
        Assert.assertFalse(policy.isAllowed(MemberRef.field("pkg.Foo", "meth1", "I")));
    }

    @SuppressWarnings("static-method") @Test public void
    testAllowField() {
        SandboxPolicy policy = SandboxPolicy.builder().allowField("pkg.Foo", "fld").build();
        Assert.assertTrue(policy.isAllowed(MemberRef.field("pkg.Foo", "fld", "I")));
        Assert.assertTrue(policy.isAllowed(MemberRef.field("pkg.Foo", "fld", "J")));
        Assert.assertFalse(policy.isAllowed(MemberRef.field("pkg.Foo", "fld2", "I")));
        Assert.assertFalse(policy.isAllowed(MemberRef.method("pkg.Foo", "fld", "()I")));
    }

    @SuppressWarnings("static-method") @Test public void
    testAllowConstructors() {
        SandboxPolicy policy = SandboxPolicy.builder().allowConstructors("pkg.Foo").build();
        Assert.assertEquals(MemberRef.Kind.CONSTRUCTOR, MemberRef.method("pkg.Foo", "<init>", "()V").getKind());
        Assert.assertTrue(policy.isAllowed(MemberRef.method("pkg.Foo", "<init>", "()V")));
        Assert.assertTrue(policy.isAllowed(MemberRef.method("pkg.Foo", "<init>", "(I)V")));
        Assert.assertFalse(policy.isAllowed(MemberRef.method("pkg.Foo", "meth", "()V")));
        Assert.assertFalse(policy.isAllowed(MemberRef.method("pkg.Bar", "<init>", "()V")));
    }

    @SuppressWarnings("static-method") @Test public void
    testAllowAllMembers() {
        SandboxPolicy policy = SandboxPolicy.builder().allowAllMembers("pkg.Foo").build();
        Assert.assertTrue(policy.isAllowed(MemberRef.method("pkg.Foo", "<init>", "()V")));
        Assert.assertTrue(policy.isAllowed(MemberRef.method("pkg.Foo", "meth", "()V")));
        Assert.assertTrue(policy.isAllowed(MemberRef.field("pkg.Foo", "fld", "I")));
        Assert.assertFalse(policy.isAllowed(MemberRef.method("pkg.Foo$Inner", "meth", "()V")));
        Assert.assertFalse(policy.isSubclassingAllowed("pkg.Foo"));
    }

    @SuppressWarnings("static-method") @Test public void
    testAllowSubclassing() {
        SandboxPolicy policy = SandboxPolicy.builder().allowSubclassing("pkg.Foo").build();
        Assert.assertTrue(policy.isSubclassingAllowed("pkg.Foo"));
        Assert.assertFalse(policy.isSubclassingAllowed("pkg.Bar"));
        Assert.assertFalse(policy.isAllowed(MemberRef.method("pkg.Foo", "<init>", "()V")));
    }

    @SuppressWarnings("static-method") @Test public void
    testInclude() {
        SandboxPolicy policy1 = SandboxPolicy.builder().allowMethods("pkg.Foo", "meth").build();
        SandboxPolicy policy2 = SandboxPolicy.builder().include(policy1).allowField("pkg.Foo", "fld").build();
        Assert.assertTrue(policy2.isAllowed(MemberRef.method("pkg.Foo", "meth", "()V")));
        Assert.assertTrue(policy2.isAllowed(MemberRef.field("pkg.Foo", "fld", "I")));
        Assert.assertFalse(policy1.isAllowed(MemberRef.field("pkg.Foo", "fld", "I")));
    }

    /**
     * Verifies that class-wide rules do not enable never-allowed members.
     */
    @SuppressWarnings("static-method") @Test public void
    testClassWideRulesDoNotEnableNeverAllowedMembers() {
        SandboxPolicy policy = SandboxPolicy.builder()
            .allowAllMembers("java.lang.System", "java.lang.reflect.Method", "java.lang.Integer", "java.io.File")
            .allowConstructors("java.lang.Thread")
            .build();

        Assert.assertFalse(policy.isAllowed(MemberRef.method(
            "java.lang.System",
            "getProperty",
            "(Ljava/lang/String;)Ljava/lang/String;"
        )));
        Assert.assertFalse(policy.isAllowed(MemberRef.method("java.lang.System", "exit", "(I)V")));
        Assert.assertFalse(policy.isAllowed(MemberRef.field("java.lang.System", "out", "Ljava/io/PrintStream;")));
        Assert.assertTrue(policy.isAllowed(MemberRef.method("java.lang.System", "nanoTime", "()J")));
        Assert.assertFalse(policy.isAllowed(MemberRef.method(
            "java.lang.reflect.Method",
            "invoke",
            "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;"
        )));
        Assert.assertFalse(policy.isAllowed(MemberRef.method(
            "java.lang.Integer",
            "getInteger",
            "(Ljava/lang/String;)Ljava/lang/Integer;"
        )));
        Assert.assertTrue(policy.isAllowed(MemberRef.method("java.lang.Integer", "valueOf", "(I)Ljava/lang/Integer;")));
        Assert.assertFalse(policy.isAllowed(MemberRef.method("java.io.File", "delete", "()Z")));
        Assert.assertFalse(policy.isAllowed(MemberRef.method("java.lang.Thread", "<init>", "()V")));
    }

    /**
     * Verifies that a never-allowed member can be enabled by naming it explicitly.
     */
    @SuppressWarnings("static-method") @Test public void
    testExplicitRulesEnableNeverAllowedMembers() {
        SandboxPolicy policy = SandboxPolicy.builder()
            .allowMethod("java.lang.System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;")
            .allowMethods("java.lang.Integer", "getInteger")
            .allowField("java.lang.System", "out")
            .build();

        Assert.assertTrue(policy.isAllowed(MemberRef.method(
            "java.lang.System",
            "getProperty",
            "(Ljava/lang/String;)Ljava/lang/String;"
        )));
        Assert.assertFalse(policy.isAllowed(MemberRef.method(
            "java.lang.System",
            "getProperty",
            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
        )));
        Assert.assertTrue(policy.isAllowed(MemberRef.method(
            "java.lang.Integer",
            "getInteger",
            "(Ljava/lang/String;)Ljava/lang/Integer;"
        )));
        Assert.assertTrue(policy.isAllowed(MemberRef.field("java.lang.System", "out", "Ljava/io/PrintStream;")));
    }

    @SuppressWarnings("static-method") @Test public void
    testIsNeverAllowed() {
        Assert.assertTrue(SandboxPolicy.isNeverAllowed(MemberRef.method(
            "java.lang.Class",
            "forName",
            "(Ljava/lang/String;)Ljava/lang/Class;"
        )));
        Assert.assertFalse(SandboxPolicy.isNeverAllowed(MemberRef.method(
            "java.lang.Class",
            "getName",
            "()Ljava/lang/String;"
        )));
        Assert.assertTrue(SandboxPolicy.isNeverAllowed(MemberRef.method("sun.misc.Unsafe", "getUnsafe", "()V")));
        Assert.assertTrue(SandboxPolicy.isNeverAllowed(MemberRef.method(
            "java.lang.invoke.MethodHandles",
            "lookup",
            "()Ljava/lang/invoke/MethodHandles$Lookup;"
        )));
        Assert.assertTrue(SandboxPolicy.isNeverAllowed(MemberRef.method(
            "java.util.Collection",
            "parallelStream",
            "()Ljava/util/stream/Stream;"
        )));
        Assert.assertFalse(SandboxPolicy.isNeverAllowed(MemberRef.method("java.lang.String", "length", "()I")));
    }

    /**
     * Verifies that the access control APIs, which allow sandboxed code to escape from a {@code Sandbox} through
     * {@code AccessController.doPrivileged()}, are never allowed, unless they are named explicitly.
     */
    @SuppressWarnings("static-method") @Test public void
    testAccessControlIsNeverAllowed() {
        MemberRef doPrivileged = MemberRef.method(
            "java.security.AccessController",
            "doPrivileged",
            "(Ljava/security/PrivilegedAction;)Ljava/lang/Object;"
        );
        MemberRef doAsPrivileged = MemberRef.method(
            "javax.security.auth.Subject",
            "doAsPrivileged",
            (
                "(Ljavax/security/auth/Subject;Ljava/security/PrivilegedAction;Ljava/security/AccessControlContext;)"
                + "Ljava/lang/Object;"
            )
        );
        MemberRef getPolicy = MemberRef.method("java.security.Policy", "getPolicy", "()Ljava/security/Policy;");
        MemberRef setProperty = MemberRef.method(
            "java.security.Security",
            "setProperty",
            "(Ljava/lang/String;Ljava/lang/String;)V"
        );
        MemberRef newAccessControlContext = MemberRef.method(
            "java.security.AccessControlContext",
            "<init>",
            "([Ljava/security/ProtectionDomain;)V"
        );
        MemberRef getPermissions = MemberRef.method(
            "java.security.ProtectionDomain",
            "getPermissions",
            "()Ljava/security/PermissionCollection;"
        );

        for (MemberRef member : new MemberRef[] {
            doPrivileged, doAsPrivileged, getPolicy, setProperty, newAccessControlContext, getPermissions,
        }) {
            Assert.assertTrue(member.toString(), SandboxPolicy.isNeverAllowed(member));
        }

        // Class-wide rules do not enable them.
        SandboxPolicy classWide = SandboxPolicy.builder()
            .allowAllMembers(
                "java.security.AccessControlContext",
                "java.security.AccessController",
                "java.security.Policy",
                "java.security.ProtectionDomain",
                "java.security.Security",
                "javax.security.auth.Subject"
            )
            .allowConstructors("java.security.AccessControlContext")
            .build();
        for (MemberRef member : new MemberRef[] {
            doPrivileged, doAsPrivileged, getPolicy, setProperty, newAccessControlContext, getPermissions,
        }) {
            Assert.assertFalse(member.toString(), classWide.isAllowed(member));
        }

        // Explicit rules do.
        SandboxPolicy explicit = SandboxPolicy.builder()
            .allowMethods("java.security.AccessController", "doPrivileged")
            .build();
        Assert.assertTrue(explicit.isAllowed(doPrivileged));
        Assert.assertFalse(explicit.isAllowed(doAsPrivileged));
    }

    @SuppressWarnings("static-method") @Test public void
    testInvalidArguments() {
        try {
            SandboxPolicy.builder().allowAllMembers("java/lang/String");
            Assert.fail();
        } catch (IllegalArgumentException iae) {
            ;
        }
        try {
            SandboxPolicy.builder().allowMethod("java.lang.String", "length", "I");
            Assert.fail();
        } catch (IllegalArgumentException iae) {
            ;
        }
    }

    @SuppressWarnings("static-method") @Test public void
    testJavaLangBasicPreset() {
        SandboxPolicy p = SandboxPolicy.JAVA_LANG_BASIC;

        Assert.assertTrue(p.isAllowed(MemberRef.method("java.lang.Object", "<init>", "()V")));
        Assert.assertTrue(p.isAllowed(MemberRef.method("java.lang.Object", "toString", "()Ljava/lang/String;")));
        Assert.assertFalse(p.isAllowed(MemberRef.method("java.lang.Object", "wait", "()V")));
        Assert.assertTrue(p.isAllowed(MemberRef.method("java.lang.Class", "getName", "()Ljava/lang/String;")));
        Assert.assertFalse(p.isAllowed(MemberRef.method(
            "java.lang.Class",
            "getDeclaredFields",
            "()[Ljava/lang/reflect/Field;"
        )));
        Assert.assertTrue(p.isAllowed(MemberRef.method("java.lang.String", "length", "()I")));
        Assert.assertTrue(p.isAllowed(MemberRef.method(
            "java.lang.AbstractStringBuilder",
            "length",
            "()I"
        )));
        Assert.assertTrue(p.isAllowed(MemberRef.method("java.lang.Math", "max", "(II)I")));
        Assert.assertTrue(p.isAllowed(MemberRef.method("java.lang.Integer", "valueOf", "(I)Ljava/lang/Integer;")));
        Assert.assertFalse(p.isAllowed(MemberRef.method(
            "java.lang.Integer",
            "getInteger",
            "(Ljava/lang/String;)Ljava/lang/Integer;"
        )));
        Assert.assertFalse(p.isAllowed(MemberRef.method(
            "java.lang.Long",
            "getLong",
            "(Ljava/lang/String;)Ljava/lang/Long;"
        )));
        Assert.assertFalse(p.isAllowed(MemberRef.method("java.lang.Boolean", "getBoolean", "(Ljava/lang/String;)Z")));
        Assert.assertTrue(p.isAllowed(MemberRef.method(
            "java.lang.RuntimeException",
            "<init>",
            "(Ljava/lang/String;)V"
        )));
        Assert.assertTrue(p.isAllowed(MemberRef.method("java.lang.Throwable", "getMessage", "()Ljava/lang/String;")));
        Assert.assertFalse(p.isAllowed(MemberRef.method("java.lang.Throwable", "printStackTrace", "()V")));
        Assert.assertFalse(p.isAllowed(MemberRef.method("java.lang.System", "exit", "(I)V")));
        Assert.assertFalse(p.isAllowed(MemberRef.method("java.lang.Thread", "<init>", "()V")));

        Assert.assertTrue(p.isSubclassingAllowed("java.lang.Object"));
        Assert.assertTrue(p.isSubclassingAllowed("java.lang.RuntimeException"));
        Assert.assertFalse(p.isSubclassingAllowed("java.lang.Thread"));
        Assert.assertFalse(p.isSubclassingAllowed("java.lang.ClassLoader"));
    }

    @SuppressWarnings("static-method") @Test public void
    testCollectionsPreset() {
        SandboxPolicy p = SandboxPolicy.COLLECTIONS;

        Assert.assertTrue(p.isAllowed(MemberRef.method("java.util.ArrayList", "<init>", "()V")));
        Assert.assertTrue(p.isAllowed(MemberRef.method("java.util.List", "get", "(I)Ljava/lang/Object;")));
        Assert.assertTrue(p.isAllowed(MemberRef.method(
            "java.util.HashMap",
            "get",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        )));
        Assert.assertTrue(p.isAllowed(MemberRef.method("java.util.Map$Entry", "getKey", "()Ljava/lang/Object;")));
        Assert.assertTrue(p.isAllowed(MemberRef.method("java.util.Arrays", "sort", "([I)V")));
        Assert.assertFalse(p.isAllowed(MemberRef.method("java.util.Arrays", "parallelSort", "([I)V")));
        Assert.assertTrue(p.isAllowed(MemberRef.method(
            "java.util.Collection",
            "stream",
            "()Ljava/util/stream/Stream;"
        )));
        Assert.assertFalse(p.isAllowed(MemberRef.method(
            "java.util.Collection",
            "parallelStream",
            "()Ljava/util/stream/Stream;"
        )));
        Assert.assertFalse(p.isAllowed(MemberRef.method(
            "java.util.ServiceLoader",
            "load",
            "(Ljava/lang/Class;)Ljava/util/ServiceLoader;"
        )));
        Assert.assertFalse(p.isAllowed(MemberRef.method("java.lang.String", "length", "()I")));

        Assert.assertTrue(p.isSubclassingAllowed("java.util.AbstractList"));
        Assert.assertFalse(p.isSubclassingAllowed("java.util.ArrayList"));
    }
}
