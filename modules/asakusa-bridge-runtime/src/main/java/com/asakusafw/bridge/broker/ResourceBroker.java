package com.asakusafw.bridge.broker;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Brokers resources between the framework and user applications.
 */
public final class ResourceBroker {

    static final Logger LOG = LoggerFactory.getLogger(ResourceBroker.class);

    static final Scope DEFAULT_SCOPE = Scope.VM;

    static final Initializer DEFAULT_INITIALIZER = new Initializer() {
        @Override
        public void accept(ResourceSession session) {
            return;
        }
    };

    private static final ResourceSessionContainer CONTAINER = new ResourceSessionContainer();

    private ResourceBroker() {
        return;
    }

    /**
     * Creates a new resource session with the Java VM scope.
     * @return the created session
     * @throws IllegalStateException if another session has been started in the scope,
     *     or with the different scope in this system
     */
    public static ResourceSession start() {
        return start(DEFAULT_SCOPE);
    }

    /**
     * Creates a new resource session.
     * @param scope the session scope
     * @return the created session
     * @throws IllegalStateException if another session has been started in the scope,
     *     or with the different scope in this system
     */
    public static ResourceSession start(Scope scope) {
        try {
            return start(scope, DEFAULT_INITIALIZER, true);
        } catch (IOException e) {
            // may not occur
            throw new AssertionError(e);
        }
    }

    /**
     * Creates a new resource session with the Java VM scope.
     * @param initializer the session initializer
     * @return the created session
     * @throws IOException if failed to initialize a new session
     * @throws IllegalStateException if another session has been started in the scope,
     *     or with the different scope in this system
     */
    public static ResourceSession attach(Initializer initializer) throws IOException {
        return attach(DEFAULT_SCOPE, initializer);
    }

    /**
     * Creates a new resource session.
     * @param scope the session scope
     * @param initializer the session initializer
     * @return the created session
     * @throws IOException if failed to initialize a new session
     * @throws IllegalStateException if another session has been started in the scope,
     *     or with the different scope in this system
     */
    public static ResourceSession attach(Scope scope, Initializer initializer) throws IOException {
        return start(scope, initializer, false);
    }

    /**
     * Closes all active sessions.
     */
    public static void closeAll() {
        CONTAINER.dispose();
    }

    /**
     * Returns a registered resource.
     * @param <T> the resource type
     * @param type the resource type
     * @return the corresponded resource, or {@code null} if there is no such a resource in the current session
     */
    public static <T> T find(Class<T> type) {
        return getCurrentSession().find(type);
    }

    /**
     * Returns a registered resource.
     * @param <T> the resource type
     * @param type the resource type
     * @return the corresponded resource
     * @throws NoSuchElementException if there is no such a resource in the current session
     */
    public static <T> T get(Class<T> type) {
        return getCurrentSession().get(type);
    }

    /**
     * Returns a registered resource, or registers a new resource.
     * @param <T> the resource type
     * @param type the resource type
     * @param supplier the resource supplier, which will be called only if there is no such a resource
     * @return the registered or created resource
     * @throws IllegalStateException if failed to create a new resource via the {@code supplier}
     */
    public static <T> T get(Class<T> type, Callable<? extends T> supplier) {
        return getCurrentSession().get(type, supplier);
    }

    /**
     * Registers a resource.
     * @param <T> the resource type
     * @param type the resource type
     * @param resource the target resource
     * @throws IllegalStateException if the target resource already exists in this current session
     */
    public static <T> void put(Class<T> type, T resource) {
        getCurrentSession().put(type, resource);
    }

    private static ResourceSession start(Scope scope, Initializer initializer, boolean strict) throws IOException {
        LOG.debug("starting session: {} (strict={})", scope, strict);
        ResourceSessionEntity entity = CONTAINER.create(scope);
        if (entity == null) {
            if (strict) {
                throw new IllegalStateException(MessageFormat.format(
                        "another session already exists in scope: {0}",
                        scope));
            }
            entity = getCurrentSession();
        } else {
            boolean success = false;
            try {
                initializer.accept(entity);
                success = true;
            } finally {
                if (success == false) {
                    entity.close();
                }
            }
        }
        return entity.newReference();
    }

    private static ResourceSessionEntity getCurrentSession() {
        ResourceSessionEntity result = CONTAINER.find();
        if (result != null) {
            return result;
        }
        throw new IllegalStateException("current session has not been started");
    }

    /**
     * Represents a mediating resource visibility.
     */
    public enum Scope {

        /**
         * Resources are visible in the current Java VM instance.
         */
        VM,

        /**
         * Resources are visible in the current thread.
         */
        THEAD,
    }

    /**
     * Initializes resource sessions.
     */
    public interface Initializer {

        /**
         * Accepts a session object and initializes it.
         * @param session the session to be initialized
         * @throws IOException if failed to initialize the session
         */
        void accept(ResourceSession session) throws IOException;
    }
}
