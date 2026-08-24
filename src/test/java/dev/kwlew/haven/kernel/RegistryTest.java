package dev.kwlew.haven.kernel;

import dev.kwlew.haven.exceptions.CircularDependencyException;
import dev.kwlew.haven.exceptions.UnresolvedDependencyException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryTest {

    // --- construction and caching -------------------------------------------------------------

    @Test
    void resolvesAndCachesASingleInstance() {
        Registry registry = new Registry();

        Leaf first = registry.resolve(Leaf.class);

        assertSame(first, registry.resolve(Leaf.class), "resolve must return the same instance");
    }

    @Test
    void injectsConstructorDependencies() {
        Registry registry = new Registry();

        Branch branch = registry.resolve(Branch.class);

        assertNotNull(branch.leaf);
        assertSame(registry.resolve(Leaf.class), branch.leaf);
    }

    @Test
    void prefersTheInjectAnnotatedConstructor() {
        Registry registry = new Registry();

        assertEquals("injected", registry.resolve(TwoConstructors.class).via);
    }

    @Test
    void fallsBackToTheGreediestConstructor() {
        Registry registry = new Registry();

        assertEquals(1, registry.resolve(GreedyPick.class).args);
    }

    // --- ordering, which Bootstrap's lifecycle depends on -------------------------------------

    @Test
    void getAllIsInDependencyThenCreationOrder() {
        Registry registry = new Registry();
        registry.resolve(Branch.class);

        List<Object> all = registry.getAll();

        assertEquals(2, all.size());
        assertInstanceOf(Leaf.class, all.get(0), "a dependency must be created before its dependent");
        assertInstanceOf(Branch.class, all.get(1));
    }

    @Test
    void getAllReversedMirrorsGetAll() {
        Registry registry = new Registry();
        registry.resolve(Branch.class);

        List<Object> forward = registry.getAll();
        List<Object> reversed = registry.getAllReversed();

        assertEquals(forward.size(), reversed.size());
        for (int i = 0; i < forward.size(); i++) {
            assertSame(forward.get(i), reversed.get(reversed.size() - 1 - i),
                    "shutdown order must be the exact mirror of startup order");
        }
    }

    /**
     * The regression that made Bootstrap run init/start/shutdown twice on the storage layer:
     * bind() puts one instance under two keys, and getAll() used to yield the map's values.
     */
    @Test
    void bindDoesNotDuplicateTheInstanceInIterationOrder() {
        Registry registry = new Registry();
        registry.bind(Contract.class, Implementation.class);

        assertEquals(1, registry.getAll().size(), "an aliased instance must be iterated once");
        assertSame(registry.resolve(Contract.class), registry.resolve(Implementation.class));
    }

    @Test
    void registeringOneInstanceUnderTwoKeysIteratesItOnce() {
        Registry registry = new Registry();
        Leaf leaf = new Leaf();

        registry.register(Leaf.class, leaf);
        registry.register(Object.class, leaf);

        assertEquals(1, registry.getAll().size());
    }

    // --- failure modes ------------------------------------------------------------------------

    @Test
    void detectsCircularDependencies() {
        Registry registry = new Registry();

        CircularDependencyException thrown =
                assertThrows(CircularDependencyException.class, () -> registry.resolve(Ping.class));

        assertTrue(thrown.getMessage().contains("Ping"), () -> "cycle should name the types: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("Pong"), () -> "cycle should name the types: " + thrown.getMessage());
    }

    @Test
    void reportsUnregisteredInterfaceDependencies() {
        Registry registry = new Registry();

        assertThrows(UnresolvedDependencyException.class, () -> registry.resolve(NeedsContract.class));
    }

    /**
     * A failing constructor must surface its own cause, not the reflection wrapper.
     */
    @Test
    void unwrapsConstructorFailures() {
        Registry registry = new Registry();

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> registry.resolve(Explodes.class));

        assertInstanceOf(IllegalStateException.class, thrown.getCause());
        assertEquals("boom", thrown.getCause().getMessage());
    }

    /**
     * Nested resolution failures must not be re-wrapped at every frame on the way out.
     */
    @Test
    void doesNotRewrapNestedResolutionFailures() {
        Registry registry = new Registry();

        assertThrows(UnresolvedDependencyException.class, () -> registry.resolve(DependsOnNeedsContract.class));
    }

    @Test
    void sealedRegistryRefusesToConstructButStillLooksUp() {
        Registry registry = new Registry();
        Leaf leaf = registry.resolve(Leaf.class);

        registry.seal();

        assertSame(leaf, registry.resolve(Leaf.class), "already-built types must stay resolvable");
        assertThrows(IllegalStateException.class, () -> registry.resolve(Branch.class));
    }

    // --- fixtures -----------------------------------------------------------------------------

    static class Leaf {}

    static class Branch {
        final Leaf leaf;

        Branch(Leaf leaf) {
            this.leaf = leaf;
        }
    }

    static class TwoConstructors {
        final String via;

        @Inject
        TwoConstructors() {
            this.via = "injected";
        }

        TwoConstructors(Leaf leaf) {
            this.via = "greedy";
        }
    }

    static class GreedyPick {
        final int args;

        GreedyPick() {
            this.args = 0;
        }

        GreedyPick(Leaf leaf) {
            this.args = 1;
        }
    }

    interface Contract {}

    static class Implementation implements Contract {}

    static class NeedsContract {
        NeedsContract(Contract contract) {
        }
    }

    static class DependsOnNeedsContract {
        DependsOnNeedsContract(NeedsContract needsContract) {
        }
    }

    static class Explodes {
        Explodes() {
            throw new IllegalStateException("boom");
        }
    }

    static class Ping {
        Ping(Pong pong) {
        }
    }

    static class Pong {
        Pong(Ping ping) {
        }
    }
}
