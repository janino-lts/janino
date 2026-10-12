
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

package other_package;

/**
 * Helper class for the tests of issue #154: A class that is not accessible from other packages, whose public and
 * protected members are accessed through the public {@link PublicSubclass}.
 */
class PackagePrivateBase {
    public static String    publicStaticField    = "publicStaticField";
    public static String    assignedStaticField  = "";
    public static int       assignedStaticInt;
    public static final int PUBLIC_CONSTANT      = 7;
    public String           publicField          = "publicField";
    protected static String protectedStaticField = "protectedStaticField";
    protected String        protectedField       = "protectedField";
    String                  packageField         = "packageField";

    public static String    publicStaticMethod()    { return "publicStaticMethod";    }
    public String           publicMethod()          { return "publicMethod";          }
    protected static String protectedStaticMethod() { return "protectedStaticMethod"; }
    protected String        protectedMethod()       { return "protectedMethod";       }
    String                  packageMethod()         { return "packageMethod";         }

    public static String overloaded(String s) { return "PackagePrivateBase.overloaded(String)"; }
}

/**
 * Helper class for the tests of issue #154: A class that is not accessible from other packages, with a public member
 * class.
 */
class PackagePrivateOuter {
    public static class PublicNested extends PackagePrivateBase {}
}
