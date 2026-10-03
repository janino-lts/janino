
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

package org.codehaus.commons.compiler.util;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.function.Supplier;

/**
 * Runs actions with the privileges of the calling code, regardless of the permissions of the code further up the
 * call stack.
 * <p>
 *   On JVMs that provide the {@code java.security.AccessController} API, this is equivalent to {@link
 *   AccessController#doPrivileged(PrivilegedAction)}. That matters on JVMs where a security manager can be installed
 *   (Java 8 through 23). Should a future JVM no longer provide that API, the action is simply executed.
 * </p>
 */
public final
class Privileged {

    private Privileged() {}

    /**
     * Whether the {@code java.security.AccessController} API is available on the running JVM.
     */
    private static final boolean ACCESS_CONTROLLER_AVAILABLE = (
        Privileged.isClassAvailable("java.security.AccessController")
        && Privileged.isClassAvailable("java.security.PrivilegedAction")
    );

    /**
     * Runs the given <var>action</var> with the privileges of the calling code.
     *
     * @return The value returned by the <var>action</var>
     */
    public static <T> T
    run(Supplier<T> action) {
        return Privileged.ACCESS_CONTROLLER_AVAILABLE ? AccessControllerBridge.doPrivileged(action) : action.get();
    }

    private static boolean
    isClassAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException cnfe) {
            return false;
        } catch (LinkageError le) {
            return false;
        }
    }

    /**
     * All references to the {@code java.security.AccessController} API are confined to this class, so that the
     * enclosing class remains loadable on JVMs that no longer provide that API.
     */
    private static final
    class AccessControllerBridge {

        private AccessControllerBridge() {}

        static <T> T
        doPrivileged(final Supplier<T> action) {
            return AccessController.doPrivileged(new PrivilegedAction<T>() {
                @Override public T run() { return action.get(); }
            });
        }
    }
}
