package io.github.avmurashov.polyglot;

import org.apache.commons.lang3.Validate;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;

import static java.lang.reflect.Modifier.isStatic;
import static java.lang.reflect.Proxy.newProxyInstance;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * This class builds the polyglot implementation of the specified messaging interface.
 *
 * @param <M> Type of the messaging interface to implement.
 */
public class Polyglot<M> {
    private final Class<M> messagingInterface;
    private final Set<Method> messagingMethods;

    private String resourceBundleName;
    private Module resourceBundleModule;
    private Locale locale;

    /**
     * Starts building the polyglot implementation of the specified messaging interface.
     *
     * @param messagingInterface Class of the messaging interface to implement.
     * @param <M> Type of the messaging interface to implement.
     * @return {@code Polyglot} instance that holds the currently supplied implementation parameters.
     */
    public static <M> Polyglot<M> forMessagingInterface(Class<M> messagingInterface) {
        return new Polyglot<>(messagingInterface);
    }

    /**
     * Constructs the {@code Polyglot} instance.
     *
     * @param messagingInterface Class of the messaging interface to implement.
     */
    private Polyglot(Class<M> messagingInterface) {
        this.messagingInterface = messagingInterface(messagingInterface);
        this.messagingMethods = messagingMethods(messagingInterface);
        this.resourceBundleName = defaultResourceBundleName(messagingInterface);
        this.resourceBundleModule = defaultResourceBundleModule(messagingInterface);
        this.locale = defaultLocale();
    }

    private Class<M> messagingInterface(Class<M> messagingInterface) {
        Validate.notNull(messagingInterface, "messagingInterface");
        Validate.isTrue(messagingInterface.isInterface(), "Not an interface: %s", messagingInterface.getName());
        return messagingInterface;
    }

    private Set<Method> messagingMethods(Class<M> messagingInterface) {
        final var visitedMethods = new HashMap<String, Method>();
        for (var method : messagingInterface.getMethods()) {
            if (isStatic(method.getModifiers())) {
                continue;   
            }

            final var methodGenericString = method.toGenericString();
            final var visitedMethodGenericString = ofNullable(visitedMethods.putIfAbsent(method.getName(), method))
                    .map(Method::toGenericString)
                    .orElse(null);

            Validate.isTrue(
                    visitedMethodGenericString == null,
                    "Overloaded methods not supported: %s VS %s",
                    visitedMethodGenericString,
                    methodGenericString);
            Validate.isTrue(
                    Objects.equals(method.getGenericReturnType(), String.class),
                    "Only String return type is supported: %s",
                    methodGenericString);
        }
        return new HashSet<>(visitedMethods.values());
    }

    private String defaultResourceBundleName(Class<M> messagingInterface) {
        return messagingInterface.getName() + "ResourceBundle";
    }

    private Module defaultResourceBundleModule(Class<M> messagingInterface) {
        return messagingInterface.getModule();
    }

    private Locale defaultLocale() {
        return Locale.getDefault();
    }

    /**
     * Define the name of the resource bundle to load.
     *
     * @param resourceBundleName Name of the resource bundle to load.
     * @return This {@code Polyglot}.
     */
    public Polyglot<M> withResourceBundleName(String resourceBundleName) {
        this.resourceBundleName = Validate.notBlank(resourceBundleName, "resourceBundleName");
        return this;
    }

    /**
     * Define the module to load resource bundle from.
     *
     * @param resourceBundleModule The module to load resource bundle from.
     * @return This {@code Polyglot}.
     */
    public Polyglot<M> withResourceBundleModule(Module resourceBundleModule) {
        this.resourceBundleModule = Validate.notNull(resourceBundleModule, "resourceBundleModule");
        return this;
    }

    /**
     * Define the desired locale of the resource bundle to load.
     *
     * @param locale Desired locale of the resource bundle to load.
     * @return This {@code Polyglot}.
     */
    public Polyglot<M> withLocale(Locale locale) {
        this.locale = Validate.notNull(locale, "locale");
        return this;
    }

    /**
     * Build the messaging interface instance.
     *
     * @return Messaging interface instance/
     */
    public M build() {
        final var resourceBundle = ResourceBundle.getBundle(resourceBundleName, locale, resourceBundleModule);

        final var methodToMessage = messagingMethods.stream().collect(toMap(
                identity(), method -> createMessage(resourceBundle, method)));

        final var invocationHandler = new MessagingInterfaceInvocationHandler(methodToMessage);

        final var proxy = newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { messagingInterface },
                invocationHandler);

        return messagingInterface.cast(proxy);
    }

    private Message createMessage(ResourceBundle resourceBundle, Method method) {
        Validate.validState(
                resourceBundle.containsKey(method.getName()),
                "Resource bundle '%s' lacks key for method: %s",
                resourceBundle.getBaseBundleName(),
                method.toGenericString());

        final var pattern = resourceBundle.getObject(method.getName()).toString();
        final var messageFormat = new MessageFormat(pattern, locale);
        return new Message(messageFormat);
    }

    private record MessagingInterfaceInvocationHandler(
            Map<Method, Message> methodToMessage) implements InvocationHandler {

        private MessagingInterfaceInvocationHandler(Map<Method, Message> methodToMessage) {
            this.methodToMessage = Map.copyOf(Validate.notEmpty(methodToMessage));
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
            if (methodToMessage.containsKey(method)) {
                return methodToMessage.get(method).format(args);
            } else {
                return method.invoke(proxy, args);
            }
        }
    }
}
