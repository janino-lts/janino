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

package org.codehaus.janino.util;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.Location;
import org.codehaus.janino.Java;
import org.codehaus.janino.Java.Annotation;
import org.codehaus.janino.Java.BlockStatement;
import org.codehaus.janino.Java.ConstructorDeclarator;
import org.codehaus.janino.Java.DoStatement;
import org.codehaus.janino.Java.ForEachStatement;
import org.codehaus.janino.Java.ForStatement;
import org.codehaus.janino.Java.MethodDeclarator;
import org.codehaus.janino.Java.NewArray;
import org.codehaus.janino.Java.Rvalue;
import org.codehaus.janino.Java.WhileStatement;

/**
 * Copies an AST and inserts the resource limit checks of {@code org.codehaus.commons.compiler.sandbox.Guard}:
 * <ul>
 *   <li>{@code Guard.tick()} at the beginning of every loop body (WHILE, DO, FOR and enhanced FOR statements; this
 *   includes iterations that start with a CONTINUE statement) and of every method and constructor body,</li>
 *   <li>{@code Guard.arrayLength(length, elementSize)} around the dimension expression of a one-dimensional array
 *   creation, and {@code Guard.newArray(componentType, dimensions)} instead of a multi-dimensional array creation
 *   (array initializers are not checked, because their size is limited by the size of the source code).</li>
 * </ul>
 * <p>
 *   JANINO applies it to code that it compiles with a {@link org.codehaus.commons.compiler.sandbox.SandboxPolicy}.
 * </p>
 */
public
class SandboxInstrumenter extends DeepCopier {

    private static final String[] GUARD = { "org", "codehaus", "commons", "compiler", "sandbox", "Guard" };

    // The (approximate) size of a reference in an array, in bytes.
    private static final int REFERENCE_SIZE = 4;

    @Override public BlockStatement
    copyWhileStatement(WhileStatement subject) throws CompileException {
        return new WhileStatement(
            subject.getLocation(),
            this.copyRvalue(subject.condition),
            this.tickAndCopy(subject.getLocation(), subject.body)
        );
    }

    @Override public BlockStatement
    copyDoStatement(DoStatement subject) throws CompileException {
        return new DoStatement(
            subject.getLocation(),
            this.tickAndCopy(subject.getLocation(), subject.body),
            this.copyRvalue(subject.condition)
        );
    }

    @Override public BlockStatement
    copyForStatement(ForStatement subject) throws CompileException {
        return new ForStatement(
            subject.getLocation(),
            this.copyOptionalBlockStatement(subject.init),
            this.copyOptionalRvalue(subject.condition),
            this.copyOptionalRvalues(subject.update),
            this.tickAndCopy(subject.getLocation(), subject.body)
        );
    }

    @Override public BlockStatement
    copyForEachStatement(ForEachStatement subject) throws CompileException {
        return new ForEachStatement(
            subject.getLocation(),
            this.copyFormalParameter(subject.currentElement),
            this.copyRvalue(subject.expression),
            this.tickAndCopy(subject.getLocation(), subject.body)
        );
    }

    @Override public MethodDeclarator
    copyMethodDeclarator(MethodDeclarator subject) throws CompileException {

        // Abstract and native methods have no body.
        List<? extends BlockStatement> statements = subject.statements;

        return new MethodDeclarator(
            subject.getLocation(),
            subject.getDocComment(),
            this.copyModifiers(subject.getModifiers()),
            this.copyOptionalTypeParameters(subject.typeParameters),
            this.copyType(subject.type),
            subject.name,
            this.copyFormalParameters(subject.formalParameters),
            this.copyTypes(subject.thrownExceptions),
            this.copyOptionalElementValue(subject.defaultValue),
            statements == null ? null : this.tickAndCopy(subject.getLocation(), statements)
        );
    }

    /**
     * The tick is inserted after the (explicit or implicit) superclass constructor invocation, which JANINO keeps
     * separately from the constructor body.
     */
    @Override public ConstructorDeclarator
    copyConstructorDeclarator(ConstructorDeclarator subject) throws CompileException {
        List<? extends BlockStatement> statements = subject.statements;
        assert statements != null;
        return new ConstructorDeclarator(
            subject.getLocation(),
            subject.getDocComment(),
            this.copyModifiers(subject.getModifiers()),
            this.copyFormalParameters(subject.formalParameters),
            this.copyTypes(subject.thrownExceptions),
            this.copyOptionalConstructorInvocation(subject.constructorInvocation),
            this.tickAndCopy(subject.getLocation(), statements)
        );
    }

    /**
     * A one-dimensional array creation "{@code new T[n]}" becomes "{@code new T[Guard.arrayLength(n, size)]}"; a
     * multi-dimensional array creation "{@code new T[n][m]}" becomes "{@code (T[][]) Guard.newArray(T.class, new
     * int[] { n, m })}", so that the guard can check the size of all arrays that are created.
     */
    @Override public Rvalue
    copyNewArray(NewArray subject) throws CompileException {

        Location location = subject.getLocation();
        Rvalue[] dimExprs = this.copyRvalues(subject.dimExprs);

        if (dimExprs.length == 1) {
            int elementSize = (
                subject.dims == 0
                ? SandboxInstrumenter.elementSize(subject.type)
                : SandboxInstrumenter.REFERENCE_SIZE
            );
            return new NewArray(
                location,
                this.copyType(subject.type),
                new Rvalue[] { SandboxInstrumenter.guardInvocation(
                    location,
                    "arrayLength",
                    dimExprs[0],
                    new Java.IntegerLiteral(location, Integer.toString(elementSize))
                ) },
                subject.dims
            );
        }

        int       dimensions    = dimExprs.length + subject.dims;
        Java.Type arrayType     = SandboxInstrumenter.arrayType(this.copyType(subject.type), dimensions);
        Java.Type componentType = SandboxInstrumenter.arrayType(this.copyType(subject.type), subject.dims);
        return new Java.Cast(
            location,
            arrayType,
            SandboxInstrumenter.guardInvocation(
                location,
                "newArray",
                new Java.ClassLiteral(location, componentType),
                new Java.NewInitializedArray(
                    location,
                    new Java.ArrayType(new Java.PrimitiveType(location, Java.Primitive.INT)),
                    new Java.ArrayInitializer(location, dimExprs)
                )
            )
        );
    }

    private static Java.Type
    arrayType(Java.Type componentType, int dimensions) {
        Java.Type result = componentType;
        for (int i = 0; i < dimensions; i++) result = new Java.ArrayType(result);
        return result;
    }

    /**
     * @return A block that ticks and then executes a copy of the <var>body</var>
     */
    private BlockStatement
    tickAndCopy(Location location, BlockStatement body) throws CompileException {
        Java.Block result = new Java.Block(location);
        result.addStatement(SandboxInstrumenter.tick(location));
        result.addStatement(this.copyBlockStatement(body));
        return result;
    }

    /**
     * @return A tick, followed by copies of the <var>statements</var>
     */
    private List<BlockStatement>
    tickAndCopy(Location location, List<? extends BlockStatement> statements) throws CompileException {
        List<BlockStatement> result = new ArrayList<BlockStatement>(statements.size() + 1);
        result.add(SandboxInstrumenter.tick(location));
        result.addAll(this.copyBlockStatements(statements));
        return result;
    }

    private static BlockStatement
    tick(Location location) throws CompileException {
        return new Java.ExpressionStatement(SandboxInstrumenter.guardInvocation(location, "tick"));
    }

    private static Java.MethodInvocation
    guardInvocation(Location location, String methodName, Rvalue... arguments) {
        return new Java.MethodInvocation(
            location,
            new Java.ReferenceType(location, new Annotation[0], SandboxInstrumenter.GUARD, null),
            methodName,
            arguments
        );
    }

    /**
     * @return The size of an array element of the given <var>type</var>, in bytes
     */
    private static int
    elementSize(Java.Type type) {

        if (!(type instanceof Java.PrimitiveType)) return SandboxInstrumenter.REFERENCE_SIZE;

        switch (((Java.PrimitiveType) type).primitive) {
        case BOOLEAN:
        case BYTE:
            return 1;
        case CHAR:
        case SHORT:
            return 2;
        case LONG:
        case DOUBLE:
            return 8;
        default:
            return 4;
        }
    }
}
