/*
 * Copyright 2010 Henry Coles
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package org.pitest.rv;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;
import org.pitest.classinfo.ClassByteArraySource;
import org.pitest.classinfo.ClassName;
import org.pitest.classpath.ClassPathByteArraySource;
import org.pitest.mutationtest.engine.Mutant;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.mutationtest.engine.gregor.GregorMutater;
import org.pitest.mutationtest.engine.gregor.MethodInfo;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.simpletest.ExcludedPrefixIsolationStrategy;
import org.pitest.simpletest.Transformation;
import org.pitest.simpletest.TransformingClassLoader;
import org.pitest.util.Unchecked;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Deprecated // use org.pitest.verifier.mutants.MutatorVerifierStart instead
public abstract class MutatorTestBase {

    protected GregorMutater engine;

    protected List<MutationDetails> findMutationsFor(
            final Class<?> clazz) {
        return this.engine.findMutations(ClassName.fromClass(clazz));
    }

    protected void createTesteeWith(final Predicate<MethodInfo> filter,
                                    final MethodMutatorFactory... mutators) {
        this.engine = new GregorMutater(new ClassPathByteArraySource(), filter,
                Arrays.asList(mutators));
    }

    protected void createTesteeWith(final ClassByteArraySource source,
                                    final Predicate<MethodInfo> filter,
                                    final Collection<MethodMutatorFactory> mutators) {
        this.engine = new GregorMutater(source, filter, mutators);
    }

    protected void createTesteeWith(final Predicate<MethodInfo> filter,
                                    final Collection<MethodMutatorFactory> mutators) {
        createTesteeWith(new ClassPathByteArraySource(), filter, mutators);
    }

    protected void createTesteeWith(final Predicate<MethodInfo> filter,
                                    final Collection<String> loggingClasses,
                                    final Collection<MethodMutatorFactory> mutators) {
        this.engine = new GregorMutater(new ClassPathByteArraySource(), filter,
                mutators);
    }

    protected void createTesteeWith(
            final Collection<MethodMutatorFactory> mutators) {
        createTesteeWith(i -> true, mutators);
    }

    protected void createTesteeWith(final MethodMutatorFactory... mutators) {
        createTesteeWith(i -> true, mutators);
    }

    protected <T> void assertMutantCallableReturns(final Callable<T> unmutated,
                                                   final Mutant mutant, final T expected) throws Exception {
        assertEquals(expected, mutateAndCall(unmutated, mutant));
    }

    protected void assertNoMutants(final Class<?> mutee) {
        final Collection<MutationDetails> actual = findMutationsFor(mutee);
        assertThat(actual).isEmpty();
    }

    protected <T> T mutateAndCall(final Callable<T> unmutated, final Mutant mutant) {
        try {
            final ClassLoader loader = createClassLoader(mutant);
            return runInClassLoader(loader, unmutated);
        } catch (final RuntimeException ex) {
            throw ex;
        } catch (final Exception ex) {
            throw Unchecked.translateCheckedException(ex);
        }
    }

    private ClassLoader createClassLoader(final Mutant mutant) throws Exception {
        return new TransformingClassLoader(
                createTransformation(mutant), new ExcludedPrefixIsolationStrategy());
    }

    private Transformation createTransformation(final Mutant mutant) {
        return (name, bytes) -> {
            if (name.equals(mutant.getDetails().getClassName().asJavaName())) {
                return mutant.getBytes();
            } else {
                return bytes;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <T> T runInClassLoader(final ClassLoader loader,
                                   final Callable<T> callable) throws Exception {
        final Callable<T> c = (Callable<T>) XStreamCloning.cloneForLoader(callable,
                loader);
        return c.call();

    }

    private Function<MutationDetails, Mutant> createMutant() {
        return a -> MutatorTestBase.this.engine.getMutation(a.getId());
    }

    protected Mutant getFirstMutant(final Collection<MutationDetails> actual) {
        assertFalse("No mutant found", actual.isEmpty());
        final Mutant mutant = this.engine.getMutation(actual.iterator().next()
                .getId());
        verifyMutant(mutant);
        return mutant;
    }

    protected Mutant getFirstMutant(final Class<?> mutee) {
        final Collection<MutationDetails> actual = findMutationsFor(mutee);
        return getFirstMutant(actual);
    }

    protected Mutant getNthMutant(final Class<?> mutee, int n) {
        final Collection<MutationDetails> actual = findMutationsFor(mutee);
        return getNthMutant(actual, n);
    }

    protected Mutant getNthMutant(final Collection<MutationDetails> actual, int n) {
        assertFalse("There are less than " + (n + 1) +" mutants", actual.size() < n + 1 );
        Iterator<MutationDetails> i = actual.iterator();
        for (int j = 0; j < n && i.hasNext(); j++) {
            i.next();
        }
        final Mutant mutant = this.engine.getMutation(i.next()
                .getId());
        verifyMutant(mutant);
        return mutant;
    }

    private void verifyMutant(final Mutant mutant) {
        // printMutant(mutant);
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        CheckClassAdapter.verify(new ClassReader(mutant.getBytes()), false, pw);
        assertTrue(sw.toString(), sw.toString().length() == 0);

    }

    protected void printMutant(final Mutant mutant) {
        final ClassReader reader = new ClassReader(mutant.getBytes());
        reader.accept(new TraceClassVisitor(null, new ASMifier(), new PrintWriter(
                System.out)), ClassReader.EXPAND_FRAMES);
    }

    protected Predicate<MethodInfo> mutateOnlyCallMethod() {
        return a -> a.getName().equals("call");
    }

}
